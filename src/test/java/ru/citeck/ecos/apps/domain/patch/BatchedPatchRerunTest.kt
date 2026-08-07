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
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
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
class BatchedPatchRerunTest {

    @Autowired
    lateinit var records: RecordsService

    @Autowired
    lateinit var patchService: EcosPatchService

    @Autowired
    lateinit var rerunCounter: RerunCounter

    @Autowired
    lateinit var emptyCounter: EmptyCounter

    @Test
    fun `manual rerun of an applied batched patch runs it again from the first batch`() {
        createPatch("rerun-batch-patch", "rerun-batch-test", (1..5).map { ObjectData.create().set("code", it) })

        val patchRef = waitForStatus("rerun-batch-patch", "APPLIED")
        assertThat(rerunCounter.processed.get()).isEqualTo(5)

        AuthContext.runAsSystem { patchService.applyPatch(patchRef.getLocalId()) }

        waitFor("all 5 records processed a second time") { rerunCounter.processed.get() == 10 }
        waitForStatus("rerun-batch-patch", "APPLIED")

        assertThat(rerunCounter.emptyBatches.get()).isZero()
        assertThat(rerunCounter.processed.get()).isEqualTo(10)
        assertThat(lastErrorOf("rerun-batch-patch")).isBlank()
    }

    @Test
    fun `batched patch without items completes without calling the target app`() {
        createPatch("empty-batch-patch", "empty-batch-test", emptyList())

        waitForStatus("empty-batch-patch", "APPLIED")

        assertThat(emptyCounter.emptyBatches.get()).isZero()
        assertThat(lastErrorOf("empty-batch-patch")).isBlank()
    }

    private fun createPatch(patchId: String, type: String, recs: List<ObjectData>) {
        AuthContext.runAsSystem {
            records.mutate(
                RecordAtts(
                    EntityRef.create(EcosPatchDesc.SRC_ID, ""),
                    ObjectData.create()
                        .set("id", patchId)
                        .set("targetApp", "eapps")
                        .set("date", "2022-01-01T00:00:00Z")
                        .set("manual", false)
                        .set("type", type)
                        .set("batch", ObjectData.create().set("field", "records").set("size", 2))
                        .set("config", ObjectData.create().set("records", recs))
                )
            )
        }
    }

    private fun getPatchRef(patchId: String): EntityRef {
        return AuthContext.runAsSystem {
            records.queryOne(
                RecordsQuery.create {
                    withSourceId(EcosPatchDesc.SRC_ID)
                    withQuery(Predicates.eq(EcosPatchDesc.ATT_PATCH_ID, patchId))
                }
            ) ?: EntityRef.EMPTY
        }
    }

    private fun getAtt(patchId: String, att: String): String {
        val ref = getPatchRef(patchId)
        if (EntityRef.isEmpty(ref)) {
            return ""
        }
        return AuthContext.runAsSystem { records.getAtt(ref, att).asText() }
    }

    private fun lastErrorOf(patchId: String): String {
        return getAtt(patchId, "lastError")
    }

    private fun waitForStatus(patchId: String, status: String): EntityRef {
        var lastStatus = ""
        waitFor("patch '$patchId' status to be $status") {
            lastStatus = getAtt(patchId, EcosPatchDesc.ATT_STATUS)
            lastStatus == status
        }
        return getPatchRef(patchId)
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > 120_000) {
                error("Timeout while waiting for $what")
            }
            Thread.sleep(500)
        }
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
