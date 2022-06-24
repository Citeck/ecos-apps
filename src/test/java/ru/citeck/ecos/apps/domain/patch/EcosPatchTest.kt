package ru.citeck.ecos.apps.domain.patch

import mu.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import ru.citeck.ecos.apps.EcosAppsApp
import ru.citeck.ecos.apps.domain.patch.config.EcosPatchConfig
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.lib.patch.PatchExecutionState
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatch
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatchDependsOn
import ru.citeck.ecos.webapp.lib.patch.executor.bean.StatefulEcosPatch
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.util.concurrent.Callable

@TestPropertySource
@Import(EcosPatchTest.TestConfig::class)
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosAppsApp::class])
class EcosPatchTest {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Autowired
    lateinit var component: TestComponent

    @Autowired
    lateinit var stateful: TestWithState

    @Autowired
    lateinit var dependentPatch: DependentPatch

    @Autowired
    lateinit var records: RecordsService

    @Test
    fun test() {
        val waitingStart = System.currentTimeMillis()
        while (!component.executed || !stateful.completed || !dependentPatch.completed) {
            if ((System.currentTimeMillis() - waitingStart) > 30_000) {
                error("Timeout exception")
            }
            Thread.sleep(1000)
        }
        log.info { "Patch applied after " + (System.currentTimeMillis() - waitingStart) + "ms" }
        Thread.sleep(1000)

        AuthContext.runAsSystem {
            val patchRes = records.queryOne(
                RecordsQuery.create {
                    withSourceId(EcosPatchConfig.REPO_ID)
                    withQuery(
                        Predicates.and(
                            Predicates.eq("targetApp", "eapps"),
                            Predicates.eq("patchId", "test-patch")
                        )
                    )
                },
                "patchResult.result"
            ).asText()

            assertThat(patchRes).isEqualTo("custom-result")

            val patchRes2 = records.queryOne(
                RecordsQuery.create {
                    withSourceId(EcosPatchConfig.REPO_ID)
                    withQuery(
                        Predicates.and(
                            Predicates.eq("targetApp", "eapps"),
                            Predicates.eq("patchId", "stateful-patch")
                        )
                    )
                },
                "state.counter?num"
            ).asInt()

            assertThat(patchRes2).isEqualTo(5)

            val patchRes3 = records.queryOne(
                RecordsQuery.create {
                    withSourceId(EcosPatchConfig.REPO_ID)
                    withQuery(
                        Predicates.and(
                            Predicates.eq("targetApp", "eapps"),
                            Predicates.eq("patchId", "dependent-patch")
                        )
                    )
                },
                "patchResult.result"
            ).asText()

            assertThat(patchRes3).isEqualTo("SUCCESS")
        }
    }

    @EcosPatch("test-patch", "2022-01-01T00:00:00Z")
    class TestComponent : Callable<Any> {
        var executed = false

        override fun call(): Any {
            println("Patch executed")
            executed = true
            return "custom-result"
        }
    }

    @EcosPatch("stateful-patch", "2022-01-01T00:00:00Z")
    class TestWithState : StatefulEcosPatch<ObjectData> {

        var completed = false

        override fun execute(state: ObjectData): PatchExecutionState<ObjectData> {
            val counter = state.get("counter", 0) + 1
            completed = counter == 5
            log.info { "Execute stateful patch. Counter: $counter, Completed: $completed" }
            return PatchExecutionState(
                ObjectData.create()
                    .set("counter", counter),
                completed
            )
        }
    }

    @EcosPatchDependsOn("stateful-patch")
    @EcosPatch("dependent-patch", date = "2022-01-01T00:00:00Z")
    class DependentPatch(private val statefulPatch: TestWithState) : Callable<String> {

        var completed = false

        override fun call(): String {
            val msg = if (!statefulPatch.completed) {
                "ERROR"
            } else {
                "SUCCESS"
            }
            completed = true
            return msg
        }
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        fun testComp(): TestComponent {
            return TestComponent()
        }
        @Bean
        fun statefulPatch(): TestWithState {
            return TestWithState()
        }
        @Bean
        fun dependsOnPatch(): DependentPatch {
            return DependentPatch(statefulPatch())
        }
    }
}
