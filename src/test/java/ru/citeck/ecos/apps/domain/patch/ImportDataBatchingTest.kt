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
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.patch.executor.EcosPatchExecutor
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.util.concurrent.atomic.AtomicInteger

@Import(ImportDataBatchingTest.TestConfig::class)
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosAppsApp::class])
class ImportDataBatchingTest {

    @Autowired lateinit var records: RecordsService
    @Autowired lateinit var counter: BatchCounter

    @Test
    fun batchedPatchProcessesAllRecordsAcrossBatches() {
        AuthContext.runAsSystem {
            val recs = (1..5).map { ObjectData.create().set("code", it) }
            records.mutate(
                RecordAtts(
                    EntityRef.create(EcosPatchDesc.SRC_ID, ""),
                    ObjectData.create()
                        .set("id", "batch-test-patch")
                        .set("targetApp", "eapps")
                        .set("date", "2022-01-01T00:00:00Z")
                        .set("manual", false)
                        .set("type", "batch-test")
                        .set("batch", ObjectData.create().set("field", "records").set("size", 2))
                        .set("config", ObjectData.create().set("records", recs))
                )
            )
        }
        val start = System.currentTimeMillis()
        while (counter.total.get() < 5) {
            if (System.currentTimeMillis() - start > 60_000) error("Timeout: only ${counter.total.get()} processed")
            Thread.sleep(500)
        }
        assertThat(counter.batches.get()).isGreaterThanOrEqualTo(3) // 2+2+1
        AuthContext.runAsSystem {
            val statusStart = System.currentTimeMillis()
            var status: String
            while (true) {
                status = records.queryOne(
                    RecordsQuery.create {
                        withSourceId(EcosPatchDesc.SRC_ID)
                        withQuery(Predicates.eq("patchId", "batch-test-patch"))
                    },
                    "status"
                ).asText()
                if (status == "APPLIED") {
                    break
                }
                if (System.currentTimeMillis() - statusStart > 15_000) {
                    error("Timeout: patch status never reached APPLIED, last status was '$status'")
                }
                Thread.sleep(500)
            }
            assertThat(status).isEqualTo("APPLIED")
        }
        // Exact count pins once-only processing: a slice boundary off-by-one that reprocessed a
        // boundary item would overshoot 5, and a skipped item would undershoot.
        assertThat(counter.total.get()).isEqualTo(5)
    }

    class BatchCounter {
        val total = AtomicInteger(0)
        val batches = AtomicInteger(0)
    }

    class BatchTestExecutor(private val counter: BatchCounter) : EcosPatchExecutor<ObjectData, Unit> {
        override fun getType() = "batch-test"
        override fun execute(config: ObjectData, state: Unit): Any {
            counter.batches.incrementAndGet()
            counter.total.addAndGet(config["records"].size())
            return "ok"
        }
    }

    @TestConfiguration
    class TestConfig {
        @Bean fun batchCounter() = BatchCounter()
        @Bean fun batchTestExecutor(counter: BatchCounter) = BatchTestExecutor(counter)
    }
}
