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
import ru.citeck.ecos.apps.domain.patch.service.EcosPatchService
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.webapp.lib.patch.executor.EcosPatchExecutor
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.util.concurrent.atomic.AtomicInteger

/**
 * A batched patch must never send an empty slice to the target app: 'mutate' and 'delete' executors
 * reject an empty records list with an error, so an exhausted (or empty from the start) batch would
 * fail the patch instead of completing it.
 */
@Import(BatchedPatchRerunTest.TestConfig::class)
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosAppsApp::class])
class BatchedPatchRerunTest : AbstractEcosPatchTest() {

    @Autowired
    lateinit var patchService: EcosPatchService

    @Autowired
    lateinit var rerunCounter: RerunCounter

    @Autowired
    lateinit var emptyCounter: EmptyCounter

    @Test
    fun rerunOfAppliedBatchedPatchStartsOver() {
        createPatch(
            "rerun-batch-patch",
            "rerun-batch-test",
            (1..5).map { ObjectData.create().set("code", it) },
            batchSize = 2
        )

        val patchRef = waitForStatus("rerun-batch-patch", STATUS_APPLIED)
        assertThat(rerunCounter.processed.get()).isEqualTo(5)

        AuthContext.runAsSystem { patchService.queuePatchApply(patchRef.getLocalId()) }

        waitFor("all 5 records processed a second time") { rerunCounter.processed.get() == 10 }
        waitForStatus("rerun-batch-patch", STATUS_APPLIED)

        assertThat(rerunCounter.emptyBatches.get()).isZero()
        assertThat(rerunCounter.processed.get())
            .describedAs("all 5 records a second time — the rerun started over, not from the stored offset")
            .isEqualTo(10)
        assertThat(lastErrorOf("rerun-batch-patch")).isBlank()
    }

    @Test
    fun batchedPatchWithoutItemsCompletesLocally() {
        createPatch("empty-batch-patch", "empty-batch-test", emptyList(), batchSize = 2)

        waitForStatus("empty-batch-patch", STATUS_APPLIED)

        assertThat(emptyCounter.emptyBatches.get())
            .describedAs("the target app must not be called at all for a batch with nothing in it")
            .isZero()
        assertThat(lastErrorOf("empty-batch-patch")).isBlank()
    }

    class RerunCounter {
        val processed = AtomicInteger(0)
        val emptyBatches = AtomicInteger(0)
    }

    class EmptyCounter {
        val emptyBatches = AtomicInteger(0)
    }

    /**
     * Mirrors MutateRecordsPatchExecutor/DeleteRecordsPatchExecutor, which fail on an empty list.
     */
    class RerunTestExecutor(private val counter: RerunCounter) : EcosPatchExecutor<ObjectData, Unit> {
        override fun getType() = "rerun-batch-test"
        override fun execute(config: ObjectData, state: Unit): Any {
            val size = config["records"].size()
            if (size == 0) {
                counter.emptyBatches.incrementAndGet()
                error("Invalid patch config. Expected 'record' or 'records' field")
            }
            counter.processed.addAndGet(size)
            return "ok"
        }
    }

    class EmptyTestExecutor(private val counter: EmptyCounter) : EcosPatchExecutor<ObjectData, Unit> {
        override fun getType() = "empty-batch-test"
        override fun execute(config: ObjectData, state: Unit): Any {
            if (config["records"].size() == 0) {
                counter.emptyBatches.incrementAndGet()
                error("Invalid patch config. Expected 'record' or 'records' field")
            }
            return "ok"
        }
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        fun rerunCounter() = RerunCounter()

        @Bean
        fun emptyCounter() = EmptyCounter()

        @Bean
        fun rerunTestExecutor(counter: RerunCounter) = RerunTestExecutor(counter)

        @Bean
        fun emptyTestExecutor(counter: EmptyCounter) = EmptyTestExecutor(counter)
    }
}
