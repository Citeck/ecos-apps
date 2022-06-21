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
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatch
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
    lateinit var records: RecordsService

    @Test
    fun test() {
        val waitingStart = System.currentTimeMillis()
        while (!component.executed) {
            if ((System.currentTimeMillis() - waitingStart) > 15_000) {
                error("Timeout exception")
            }
            Thread.sleep(1000)
        }
        log.info { "Patch applied after " + (System.currentTimeMillis() - waitingStart) + "ms" }

        AuthContext.runAsSystem {
            val patchRes = records.queryOne(RecordsQuery.create {
                withSourceId(EcosPatchConfig.REPO_ID)
                withQuery(Predicates.and(
                    Predicates.eq("targetApp", "eapps"),
                    Predicates.eq("patchId", "test-patch")
                ))
            }, "patchResult.result").asText()

            assertThat(patchRes).isEqualTo("custom-result")
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

    @TestConfiguration
    class TestConfig {
        @Bean
        fun testComp(): TestComponent {
            return TestComponent()
        }
    }
}
