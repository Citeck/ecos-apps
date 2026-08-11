package ru.citeck.ecos.apps.domain.patch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import ru.citeck.ecos.apps.EcosAppsApp
import ru.citeck.ecos.apps.app.domain.artifact.source.AppSourceKey
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType
import ru.citeck.ecos.apps.app.domain.artifact.source.SourceKey
import ru.citeck.ecos.apps.app.domain.artifact.type.ArtifactTypeProvider
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.DeployStatus
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService
import ru.citeck.ecos.apps.domain.patch.desc.EcosPatchDesc
import ru.citeck.ecos.apps.domain.patch.service.EcosPatchService
import ru.citeck.ecos.apps.domain.patch.service.PatchDeploySyncService
import ru.citeck.ecos.apps.eapps.dto.ArtifactUploadDto
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.value.AttValue
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.SupportsQueryLanguages
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Proves the artifact-deploy wake for `dependsOnRefs`, driven by the persisted deploy watermark
 * (see [EcosPatchService.reconcileDepsWaitingPatches], [PatchDeploySyncService] and
 * [EcosArtifactsService.addArtifactDeployedListener]).
 *
 * `DEPS_WAITING` patches carry no `nextExecDate`, so the scheduler never polls them again once
 * parked there — the ONLY way such a patch can progress is the deploy advancing the watermark, which
 * the patch job then reconciles. This test deploys an `import-data` patch pointing at a type artifact
 * that isn't deployed yet (parks it in DEPS_WAITING), then deploys the type artifact and asserts the
 * patch reaches APPLIED without any other trigger.
 *
 * The executor resolves `typeRef`'s sourceId/app via `EcosTypesRegistry`, so the test registers a
 * matching `TypeDef` (id=`test-import-type-wake`, sourceId=`test-source`) directly into the
 * registry (see `setup()`).
 */
@Import(ImportDataDepsWaitWakeTest.TestConfig::class)
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosAppsApp::class])
class ImportDataDepsWaitWakeTest {

    companion object {
        // recordsSourceId of the test type app/jsontest is hardcoded to "test-source" in the
        // fixture (see src/test/resources/eapps/types/app/jsontest/type.yml) — must match exactly
        // for PatchRefsDeployChecker.allDeployed to resolve the ref's artifactType.
        private const val DATA_SRC = "test-source"
        private const val JSONTEST_TYPE = "app/jsontest"
        private const val TYPE_LOCAL_ID = "test-import-type-wake"
        private const val TYPE_REF = "eapps/$DATA_SRC@$TYPE_LOCAL_ID"
    }

    @Autowired lateinit var records: RecordsService
    @Autowired lateinit var ecosArtifactsService: EcosArtifactsService
    @Autowired lateinit var ecosArtifactTypesService: EcosArtifactTypesService
    @Autowired lateinit var artifactTypesProvider: ArtifactTypeProvider
    @Autowired lateinit var typesRegistry: EcosTypesRegistry

    @BeforeEach
    fun setup() {
        AuthContext.runAsSystem {
            ecosArtifactTypesService.registerTypes("eapps", artifactTypesProvider.getArtifactTypesDir(), Instant.now())
        }
        // ImportDataPatchExecutor resolves typeRef's sourceId/app via EcosTypesRegistry, so the
        // test type must be present there with a matching sourceId.
        typesRegistry.setValue(
            TYPE_LOCAL_ID,
            TypeDef.create {
                withId(TYPE_LOCAL_ID)
                withSourceId(DATA_SRC)
            }
        )
    }

    @Test
    fun depsWaitingPatchIsWokenByArtifactDeploy() {
        val patchId = "import-data-deps-wait-wake"

        // 1. Deploy the patch while its typeRef artifact is NOT deployed yet => gate parks it in
        //    DEPS_WAITING (no nextExecDate => not polled anymore by the scheduler).
        deployImportDataPatch(
            patchId = patchId,
            typeRef = TYPE_REF,
            records = listOf(ObjectData.create().set("code", "1"))
        )

        waitUntil(
            timeoutMsg = "patch should be parked in DEPS_WAITING by the dependsOnRefs gate " +
                "before the type artifact is deployed"
        ) {
            val status = currentPatchStatus(patchId)
            assertThat(status)
                .`as`("patch pointing at an undeployed typeRef must never be APPLIED before deploy")
                .isNotEqualTo("APPLIED")
            status == "DEPS_WAITING"
        }
        assertThat(currentPatchStatus(patchId)).isEqualTo("DEPS_WAITING")

        // 2. NOW deploy the type artifact (USER source => DEPLOYED immediately, firing the new
        //    artifact-deployed listener hook).
        AuthContext.runAsSystem {
            val uploaded = ecosArtifactsService.uploadArtifact(
                ArtifactUploadDto(
                    JSONTEST_TYPE,
                    ObjectData.create().set("id", TYPE_LOCAL_ID),
                    AppSourceKey("test-app", SourceKey("user", ArtifactSourceType.USER)),
                    ""
                )
            )
            assertThat(uploaded).`as`("test type artifact must upload").isTrue()

            val artifact = ecosArtifactsService.getLastArtifact(ArtifactRef.create(JSONTEST_TYPE, TYPE_LOCAL_ID))
            assertThat(artifact).isNotNull
            assertThat(artifact!!.deployStatus)
                .`as`("type artifact must be DEPLOYED to satisfy the dependsOnRefs gate")
                .isEqualTo(DeployStatus.DEPLOYED)
        }

        // 3. The deploy hook must reactively wake the parked patch (DEPS_WAITING -> IN_PROGRESS ->
        //    APPLIED) without any other external trigger — proving the reactive wake works, since
        //    the scheduler no longer polls DEPS_WAITING patches.
        waitUntil(
            timeoutMsg = "patch never reached APPLIED after its dependsOnRefs artifact was deployed " +
                "-- the reactive deploy-wake hook did not fire"
        ) {
            val status = currentPatchStatus(patchId)
            if (status == "FAILED") {
                val err = AuthContext.runAsSystem {
                    records.queryOne(
                        RecordsQuery.create {
                            withSourceId(EcosPatchDesc.SRC_ID)
                            withQuery(Predicates.eq("patchId", patchId))
                        },
                        "lastError"
                    ).asText()
                }
                error("Patch '$patchId' FAILED: $err")
            }
            status == "APPLIED"
        }

        AuthContext.runAsSystem {
            val created = records.queryOne(
                RecordsQuery.create {
                    withSourceId("eapps/$DATA_SRC")
                    withQuery(Predicates.eq("code", "1"))
                }
            )
            assertThat(created.toString())
                .`as`("record should have been upserted once the parked patch was applied")
                .isEqualTo("eapps/$DATA_SRC@ti-1")
        }
    }

    private fun deployImportDataPatch(patchId: String, typeRef: String, records: List<ObjectData>) {
        AuthContext.runAsSystem {
            this.records.mutate(
                RecordAtts(
                    EntityRef.create(EcosPatchDesc.SRC_ID, ""),
                    ObjectData.create()
                        .set("id", patchId)
                        .set("targetApp", "eapps")
                        .set("date", "2022-01-01T00:00:00Z")
                        .set("manual", false)
                        .set("type", "import-data")
                        .set("batch", ObjectData.create().set("field", "records").set("size", 20))
                        .set(
                            "config",
                            ObjectData.create()
                                .set("typeRef", typeRef)
                                .set("keyAtts", listOf("code"))
                                .set("templateAtts", ObjectData.create().set("id", "ti-{{code}}"))
                                .set("records", records)
                        )
                )
            )
        }
    }

    private fun currentPatchStatus(patchId: String): String {
        return AuthContext.runAsSystem {
            records.queryOne(
                RecordsQuery.create {
                    withSourceId(EcosPatchDesc.SRC_ID)
                    withQuery(Predicates.eq("patchId", patchId))
                },
                "status"
            ).asText()
        }
    }

    private fun waitUntil(timeoutMs: Long = 120_000, timeoutMsg: String, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (condition()) {
                return
            }
            Thread.sleep(500)
        }
        error(timeoutMsg)
    }

    /**
     * In-memory records DAO registered at `eapps/test-source`: the data record source used for
     * upsert-by-id on mutate + predicate query, so the executor can create imported records
     * in-process. (sourceId/app resolution for `typeRef` itself goes through `EcosTypesRegistry`,
     * not through this DAO — see the registry seeding in `setup()`.)
     */
    class UpsertRecordsDao(private val sourceId: String) :
        AbstractRecordsDao(),
        RecordAttsDao,
        RecordMutateDao,
        RecordsQueryDao,
        SupportsQueryLanguages {

        private val records = ConcurrentHashMap<String, ObjectData>()

        override fun getId() = sourceId

        override fun getRecordAtts(recordId: String): Any? = records[recordId]

        override fun mutate(record: LocalRecordAtts): String {
            val effId = record.id
                .ifEmpty { record.attributes.get("id", "") }
                .ifEmpty { UUID.randomUUID().toString() }
            val merged = records[effId]?.deepCopy() ?: ObjectData.create()
            record.attributes.forEach { k, v -> merged.set(k, v) }
            records[effId] = merged
            return effId
        }

        override fun queryRecords(recsQuery: RecordsQuery): Any {
            val all = records.entries.map { Record(it.key, it.value) }
            if (recsQuery.language == "") {
                return all
            }
            if (recsQuery.language != PredicateService.LANGUAGE_PREDICATE) {
                error("Unsupported query language: ${recsQuery.language}")
            }
            val predicate = recsQuery.getQuery(Predicate::class.java)
            return predicateService.filterAndSort(
                all,
                predicate,
                recsQuery.sortBy,
                recsQuery.page.skipCount,
                recsQuery.page.maxItems
            )
        }

        override fun getSupportedLanguages(): List<String> = listOf("", PredicateService.LANGUAGE_PREDICATE)

        private class Record(val recId: String, val atts: ObjectData) : AttValue {
            override fun getId(): Any = recId
            override fun getAtt(name: String): Any? = atts.get(name)
        }
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        fun testImportUpsertDaoWake(): UpsertRecordsDao = UpsertRecordsDao(DATA_SRC)
    }
}
