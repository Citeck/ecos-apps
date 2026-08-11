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
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.webapp.lib.patch.executor.EcosPatchExecutor
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A patch waiting on another one is handed straight to the job when its dependency completes, not
 * woken into PENDING.
 *
 * PENDING is the status [ru.citeck.ecos.apps.domain.patch.service.EcosPatchService] watches to decide
 * whether an app's patches are still being collected from deploy sources. Waking a patch by writing
 * PENDING therefore re-armed that gate for the whole app, and every link of a dependsOn chain paid
 * `appReadyThresholdDuration` of dead time before it could start — while the job sat idle. It also
 * stranded `manual` patches for good, since the job's PENDING branch never takes them.
 */
@Import(PatchDependsOnWakeTest.TestConfig::class)
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosAppsApp::class])
class PatchDependsOnWakeTest : AbstractEcosPatchTest() {

    companion object {
        private const val PATCH_TYPE = "deps-wake-test"
        private const val LEADER_PATCH_ID = "deps-wake-leader-patch"
        private const val FOLLOWER_PATCH_ID = "deps-wake-follower-patch"
    }

    @Autowired
    lateinit var counter: WakeCounter

    @Test
    fun patchWaitingOnAnotherOneIsNotWokenIntoPending() {
        // The follower is deployed ALONE, so its dependency cannot be satisfied and DEPS_WAITING is
        // a state it rests in rather than passes through. Deploying both at once would park it and
        // apply the dependency inside a single job tick, leaving a window too short to observe.
        createPatch(
            FOLLOWER_PATCH_ID,
            PATCH_TYPE,
            listOf(ObjectData.create().set("code", 2)),
            batchSize = 1,
            dependsOn = listOf(LEADER_PATCH_ID)
        )

        waitForStatus(FOLLOWER_PATCH_ID, STATUS_DEPS_WAITING)

        createPatch(
            LEADER_PATCH_ID,
            PATCH_TYPE,
            listOf(ObjectData.create().set("code", 1)),
            batchSize = 1
        )

        // From here on the follower must never be seen PENDING again: that would mean it was woken
        // into the status the settle gate watches, and it would then sit out appReadyThresholdDuration.
        val seenStatuses = ConcurrentLinkedQueue<String>()
        waitFor("the waiting patch to be applied once its dependency is") {
            val status = statusOf(FOLLOWER_PATCH_ID)
            seenStatuses.add(status)
            status == STATUS_APPLIED
        }

        assertThat(seenStatuses)
            .describedAs("statuses the waiting patch went through after its dependency was applied")
            .doesNotContain(STATUS_PENDING)
        assertThat(statusOf(LEADER_PATCH_ID)).isEqualTo(STATUS_APPLIED)
        assertThat(counter.processedCodes.sorted())
            .describedAs("dependency first, then the patch that waited for it")
            .isEqualTo(listOf(1, 2))
    }

    class WakeCounter {
        val processedCodes = ConcurrentLinkedQueue<Int>()
    }

    class WakeTestExecutor(private val counter: WakeCounter) : EcosPatchExecutor<ObjectData, Unit> {

        override fun getType() = PATCH_TYPE

        override fun execute(config: ObjectData, state: Unit): Any {
            config["records"].forEach { counter.processedCodes.add(it["code"].asInt()) }
            return "ok"
        }
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        fun wakeCounter() = WakeCounter()

        @Bean
        fun wakeTestExecutor(counter: WakeCounter) = WakeTestExecutor(counter)
    }
}
