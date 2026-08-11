package ru.citeck.ecos.apps.domain.patch

import org.springframework.beans.factory.annotation.Autowired
import ru.citeck.ecos.apps.domain.patch.desc.EcosPatchDesc
import ru.citeck.ecos.apps.domain.patch.service.PatchBatchConfig
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef

/**
 * Deploying a patch and watching what the patch job does to it, shared by the patch job tests.
 * Patches are driven asynchronously by the job, so every assertion about them is a wait.
 */
abstract class AbstractEcosPatchTest {

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_APPLIED = "APPLIED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_DEPS_WAITING = "DEPS_WAITING"

        private const val WAIT_TIMEOUT_MS = 120_000L
    }

    @Autowired
    lateinit var records: RecordsService

    /**
     * Deploys a patch the same way an artifact deploy does. [batchField] is what decides whether the
     * patch is batched at all — blank deploys it non-batched, and [batchSize] is then irrelevant.
     */
    protected fun createPatch(
        patchId: String,
        type: String,
        recs: List<ObjectData>,
        batchSize: Int = PatchBatchConfig.DEFAULT_SIZE,
        batchField: String = "records",
        manual: Boolean = false,
        date: String = "2022-01-01T00:00:00Z",
        dependsOn: List<String> = emptyList()
    ) {
        AuthContext.runAsSystem {
            records.mutate(
                RecordAtts(
                    EntityRef.create(EcosPatchDesc.SRC_ID, ""),
                    ObjectData.create()
                        .set("id", patchId)
                        .set("targetApp", "eapps")
                        .set("date", date)
                        .set("manual", manual)
                        .set("type", type)
                        .set("dependsOn", dependsOn)
                        .set("batch", ObjectData.create().set("field", batchField).set("size", batchSize))
                        .set("config", ObjectData.create().set("records", recs))
                )
            )
        }
    }

    protected fun getPatchRef(patchId: String): EntityRef {
        return AuthContext.runAsSystem {
            records.queryOne(
                RecordsQuery.create {
                    withSourceId(EcosPatchDesc.SRC_ID)
                    withQuery(Predicates.eq(EcosPatchDesc.ATT_PATCH_ID, patchId))
                }
            ) ?: EntityRef.EMPTY
        }
    }

    protected fun getAtt(patchId: String, att: String): String {
        val ref = getPatchRef(patchId)
        if (EntityRef.isEmpty(ref)) {
            return ""
        }
        return AuthContext.runAsSystem { records.getAtt(ref, att).asText() }
    }

    protected fun statusOf(patchId: String): String {
        return getAtt(patchId, EcosPatchDesc.ATT_STATUS)
    }

    protected fun lastErrorOf(patchId: String): String {
        return getAtt(patchId, EcosPatchDesc.ATT_LAST_ERROR)
    }

    /**
     * Overwrites run state of an already deployed patch, to put it into a state that would otherwise
     * take the real retry delays (up to hours) to reach.
     */
    protected fun setRunState(patchId: String, atts: ObjectData) {
        AuthContext.runAsSystem {
            records.mutate(getPatchRef(patchId), atts)
        }
    }

    protected fun waitForStatus(patchId: String, status: String): EntityRef {
        waitFor("patch '$patchId' status to be $status") { statusOf(patchId) == status }
        return getPatchRef(patchId)
    }

    protected fun waitFor(what: String, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > WAIT_TIMEOUT_MS) {
                error("Timeout while waiting for $what")
            }
            Thread.sleep(50)
        }
    }
}
