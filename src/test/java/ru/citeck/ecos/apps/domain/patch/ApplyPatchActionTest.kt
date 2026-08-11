package ru.citeck.ecos.apps.domain.patch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import ru.citeck.ecos.apps.EcosAppsApp
import ru.citeck.ecos.apps.domain.patch.desc.EcosPatchDesc
import ru.citeck.ecos.apps.domain.patch.service.EcosPatchService
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.webapp.lib.patch.executor.EcosPatchExecutor
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * The explicit "apply patch" action only queues a patch; the patch job alone executes it.
 *
 * The action used to execute the patch in the caller's thread, so it could drive a patch the job was
 * already driving: both read the stored batch offset, both executed a batch and both wrote their own
 * result back, so the patch ended up FAILED although all its data was applied — and once errorsCount
 * outgrew the retry delays it was left with nextExecDate = null, out of reach of the job's query.
 */
@Import(ApplyPatchActionTest.TestConfig::class)
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosAppsApp::class])
class ApplyPatchActionTest : AbstractEcosPatchTest() {

    companion object {
        private const val RACE_PATCH_ID = "race-batch-patch"
        private const val RACE_PATCH_TYPE = "race-batch-test"
        private const val RACE_RECORDS_COUNT = 40

        private const val MANUAL_PATCH_ID = "manual-launch-patch"
        private const val MANUAL_PACER_PATCH_ID = "manual-launch-pacer-patch"
        private const val MANUAL_PATCH_TYPE = "manual-launch-test"
        private const val MANUAL_PATCH_DATE = "2022-01-01T00:00:00Z"
        private const val MANUAL_PACER_PATCH_DATE = "2022-01-02T00:00:00Z"

        private const val FAILING_PATCH_ID = "exhausted-retries-patch"
        private const val FAILING_PATCH_TYPE = "exhausted-retries-test"

        // Above any errorsCount errorDelayDistribution still schedules a retry for, so the patch is
        // in the terminal "no nextExecDate, never selected again" state.
        private const val EXHAUSTED_ERRORS_COUNT = 99
    }

    @Autowired
    lateinit var patchService: EcosPatchService

    @Autowired
    lateinit var raceCounter: RaceCounter

    @Autowired
    lateinit var manualCounter: ManualCounter

    @Test
    fun actionNeverExecutesPatchTheJobIsAlreadyDriving() {
        createPatch(
            RACE_PATCH_ID,
            RACE_PATCH_TYPE,
            (1..RACE_RECORDS_COUNT).map { ObjectData.create().set("code", it) },
            batchSize = 1
        )

        // Only the job can produce the first IN_PROGRESS: the action isn't fired yet. So once we see
        // it, the job is driving the patch, and firing the action now is exactly the "second driver
        // on the same patch" case.
        val patchRef = waitForStatus(RACE_PATCH_ID, STATUS_IN_PROGRESS)

        val callerThread = Thread.currentThread().name
        // Stops well before the last batch: firing at a patch that has just reached APPLIED is a
        // legitimate restart (state reset, all records again), which would make the exactly-once
        // assertion below depend on timing instead of on the guarantee under test.
        val firingUntilProcessed = RACE_RECORDS_COUNT - 10
        val firingDeadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < firingDeadline &&
            raceCounter.processedCodes.size < firingUntilProcessed
        ) {
            AuthContext.runAsSystem { patchService.queuePatchApply(patchRef.getLocalId()) }
            Thread.sleep(20)
        }

        waitForStatus(RACE_PATCH_ID, STATUS_APPLIED)

        // The sharpest assertion of the lot: a second writer on the row rolls the stored batch
        // offset back, so records get executed again. Counting threads cannot see that — the job
        // stays the only executor either way.
        assertThat(raceCounter.processedCodes.sorted())
            .describedAs("every record of the patch executed exactly once")
            .isEqualTo((1..RACE_RECORDS_COUNT).toList())
        assertThat(raceCounter.threads)
            .describedAs("threads that executed batches — only the patch job may")
            .hasSize(1)
        assertThat(raceCounter.threads).doesNotContain(callerThread)
        assertThat(raceCounter.maxConcurrent.get())
            .describedAs("batches of one patch executed at the same time")
            .isEqualTo(1)
        assertThat(raceCounter.emptyBatches.get()).isZero()
    }

    /**
     * The action's primary purpose: a `manual` patch is never launched by the job on its own, an
     * operator starts it. Queueing it as PENDING would not work — the job's PENDING branch is
     * restricted to non-manual patches — so the action has to hand it over in a state the job takes
     * regardless of the flag.
     */
    @Test
    fun actionLaunchesManualPatchThatJobLeavesAlone() {
        createPatch(
            MANUAL_PATCH_ID,
            MANUAL_PATCH_TYPE,
            (1..3).map { ObjectData.create().set("code", it) },
            batchSize = 1,
            manual = true,
            date = MANUAL_PATCH_DATE
        )
        // Deployed alongside and identical but for the flag. Its completion is the proof that the
        // job did reach this app's patches — without it, "still PENDING" would also be satisfied by
        // a job that simply hadn't got here yet (the settle gate alone delays the first tick by
        // appReadyThresholdDuration). The job sorts by date ascending, and the pacer's is the LATER
        // one, so a job that ignored `manual` would have had to start the manual patch first — which
        // is what makes "pacer APPLIED" a sound proof that the manual patch was deliberately passed.
        createPatch(
            MANUAL_PACER_PATCH_ID,
            MANUAL_PATCH_TYPE,
            listOf(ObjectData.create().set("code", 100)),
            batchSize = 1,
            date = MANUAL_PACER_PATCH_DATE
        )

        waitForStatus(MANUAL_PACER_PATCH_ID, STATUS_APPLIED)

        assertThat(statusOf(MANUAL_PATCH_ID))
            .describedAs("the job must not start a manual patch by itself")
            .isEqualTo(STATUS_PENDING)
        assertThat(manualCounter.processed.get())
            .describedAs("only the non-manual patch deployed next to it may have run")
            .isEqualTo(1)

        AuthContext.runAsSystem { patchService.queuePatchApply(getPatchRef(MANUAL_PATCH_ID).getLocalId()) }

        waitForStatus(MANUAL_PATCH_ID, STATUS_APPLIED)

        assertThat(manualCounter.processed.get())
            .describedAs("3 records of the manual patch on top of the pacer's single one")
            .isEqualTo(4)
        assertThat(manualCounter.threads)
            .describedAs("threads that executed batches — only the patch job may")
            .hasSize(1)
    }

    /**
     * A patch that used up errorDelayDistribution is left with nextExecDate = null and drops out of
     * the job's query for good — the "unrecoverable patch" of the defect report. Queueing it has to
     * hand back the full retry budget, otherwise the very next failure parks it out of reach again
     * and the operator's rescue lasts exactly one attempt.
     */
    @Test
    fun actionGivesExhaustedPatchFreshBudget() {
        createPatch(
            FAILING_PATCH_ID,
            FAILING_PATCH_TYPE,
            listOf(ObjectData.create().set("code", 1)),
            batchField = ""
        )

        waitForStatus(FAILING_PATCH_ID, STATUS_FAILED)

        // Fast-forward to the exhausted state: the real path there costs 1min+1min+...+3h of delays.
        // nextExecDate stays in the future, so the job cannot touch the row while we set this up.
        setRunState(
            FAILING_PATCH_ID,
            ObjectData.create()
                .set(EcosPatchDesc.ATT_ERRORS_COUNT, EXHAUSTED_ERRORS_COUNT)
                .set(EcosPatchDesc.ATT_NEXT_EXEC_DATE, Instant.now().plus(Duration.ofHours(1)))
        )

        AuthContext.runAsSystem { patchService.queuePatchApply(getPatchRef(FAILING_PATCH_ID).getLocalId()) }

        val exhaustedCount = EXHAUSTED_ERRORS_COUNT.toString()
        waitFor("the queued patch to be executed and to fail again") {
            val errorsCount = getAtt(FAILING_PATCH_ID, EcosPatchDesc.ATT_ERRORS_COUNT)
            errorsCount != exhaustedCount && errorsCount != "0"
        }

        assertThat(getAtt(FAILING_PATCH_ID, EcosPatchDesc.ATT_ERRORS_COUNT))
            .describedAs("the failure after the rescue must be counted as the first one")
            .isEqualTo("1")
        assertThat(getAtt(FAILING_PATCH_ID, EcosPatchDesc.ATT_NEXT_EXEC_DATE))
            .describedAs("the patch must still be reachable by the job")
            .isNotBlank()
    }

    class RaceCounter {
        val concurrent = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val emptyBatches = AtomicInteger(0)
        val threads: MutableSet<String> = ConcurrentHashMap.newKeySet()

        /** Every `code` handed to the executor, duplicates included — that is the point. */
        val processedCodes = ConcurrentLinkedQueue<Int>()
    }

    class ManualCounter {
        val processed = AtomicInteger(0)
        val threads: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }

    class RaceTestExecutor(private val counter: RaceCounter) : EcosPatchExecutor<ObjectData, Unit> {

        override fun getType() = RACE_PATCH_TYPE

        override fun execute(config: ObjectData, state: Unit): Any {
            val running = counter.concurrent.incrementAndGet()
            try {
                counter.maxConcurrent.getAndUpdate { maxOf(it, running) }
                counter.threads.add(Thread.currentThread().name)
                if (config["records"].size() == 0) {
                    counter.emptyBatches.incrementAndGet()
                    error("Invalid patch config. Expected 'record' or 'records' field")
                }
                config["records"].forEach { counter.processedCodes.add(it["code"].asInt()) }
                // Widens the window in which a second driver can enter the same patch.
                Thread.sleep(150)
                return "ok"
            } finally {
                counter.concurrent.decrementAndGet()
            }
        }
    }

    class ManualTestExecutor(private val counter: ManualCounter) : EcosPatchExecutor<ObjectData, Unit> {

        override fun getType() = MANUAL_PATCH_TYPE

        override fun execute(config: ObjectData, state: Unit): Any {
            counter.threads.add(Thread.currentThread().name)
            counter.processed.addAndGet(config["records"].size())
            return "ok"
        }
    }

    /**
     * Always fails, the way a patch whose target data is broken does.
     */
    class FailingTestExecutor : EcosPatchExecutor<ObjectData, Unit> {

        override fun getType() = FAILING_PATCH_TYPE

        override fun execute(config: ObjectData, state: Unit): Any {
            error("Patch executor failed on purpose")
        }
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        fun raceCounter() = RaceCounter()

        @Bean
        fun manualCounter() = ManualCounter()

        @Bean
        fun raceTestExecutor(counter: RaceCounter) = RaceTestExecutor(counter)

        @Bean
        fun manualTestExecutor(counter: ManualCounter) = ManualTestExecutor(counter)

        @Bean
        fun failingTestExecutor() = FailingTestExecutor()
    }
}
