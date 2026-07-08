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
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Integration test for the `import-data` patch type.
 *
 * Test 1 proves the `dependsOnRefs` soft-gate: a patch whose `typeRef` cannot be mapped to a
 * DEPLOYED eapps artifact is parked in DEPS_WAITING and never executed.
 *
 * Test 2 drives a full upsert end-to-end. To pass the gate we register the test `app/jsontest`
 * artifact type (recordsSourceId=`test-source`) and upload a DEPLOYED artifact of that type whose
 * extId matches the `typeRef` localId. The executor resolves sourceId/app for `typeRef` via
 * `EcosTypesRegistry`, so the test registers a matching `TypeDef` (id=`test-import-type`,
 * sourceId=`test-source`) directly into the registry. An in-memory upsert DAO registered at
 * `eapps/test-source` stores the imported records.
 */
@Import(ImportDataPatchTest.TestConfig::class)
@ExtendWith(ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension::class)
@SpringBootTest(classes = [EcosAppsApp::class])
class ImportDataPatchTest {

    companion object {
        // recordsSourceId of the test type app/jsontest (see src/test/resources/eapps/types/app/jsontest/type.yml)
        private const val DATA_SRC = "test-source"
        private const val JSONTEST_TYPE = "app/jsontest"
        private const val TYPE_LOCAL_ID = "test-import-type"

        // typeRef: sourceId segment (test-source) maps to the jsontest artifact type; localId matches
        // the DEPLOYED artifact we upload. This is what the dependsOnRefs gate resolves.
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
    fun `patch with undeployed typeRef is parked in DEPS_WAITING and never applied`() {
        val patchId = "import-data-deps-waiting"
        // sourceId 'nonexistent-src' is not registered as any artifact type => getTypeIdForRecordRef == ""
        // => allDeployed() == false => the patch must stay in DEPS_WAITING and never reach APPLIED.
        deployImportDataPatch(
            patchId = patchId,
            typeRef = "eapps/nonexistent-src@missing-type",
            records = listOf(ObjectData.create().set("code", "1"))
        )

        val start = System.currentTimeMillis()
        var reachedDepsWaiting = false
        while (System.currentTimeMillis() - start < 120_000) {
            val status = currentPatchStatus(patchId)
            assertThat(status)
                .`as`("patch pointing at an undeployed typeRef must never be APPLIED")
                .isNotEqualTo("APPLIED")
            if (status == "DEPS_WAITING") {
                reachedDepsWaiting = true
                break
            }
            Thread.sleep(500)
        }
        assertThat(reachedDepsWaiting)
            .`as`("patch should be parked in DEPS_WAITING by the dependsOnRefs gate")
            .isTrue()

        // give the orchestrator a moment; it must not slip through to APPLIED afterwards
        Thread.sleep(3000)
        assertThat(currentPatchStatus(patchId)).isEqualTo("DEPS_WAITING")
    }

    @Test
    fun `import-data patch upserts records once the type artifact is deployed`() {
        // 1. Deploy a real artifact of the jsontest type whose extId == typeRef localId.
        //    USER source => uploadArtifact marks it DEPLOYED, so the dependsOnRefs gate passes.
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
                .`as`("gate requires the type artifact to be DEPLOYED")
                .isEqualTo(DeployStatus.DEPLOYED)
        }

        // 2. Deploy an import-data patch that creates two records.
        val patchId1 = "import-data-upsert-1"
        deployImportDataPatch(
            patchId = patchId1,
            typeRef = TYPE_REF,
            records = listOf(
                ObjectData.create().set("code", "911").set("gen", "1"),
                ObjectData.create().set("code", "912").set("gen", "1")
            )
        )
        waitForApplied(patchId1)

        AuthContext.runAsSystem {
            // records were created with template-resolved ids
            val created = records.queryOne(
                RecordsQuery.create {
                    withSourceId("eapps/$DATA_SRC")
                    withQuery(Predicates.eq("code", "911"))
                }
            )
            assertThat(created.toString()).isEqualTo("eapps/$DATA_SRC@ti-911")

            val created2 = records.queryOne(
                RecordsQuery.create {
                    withSourceId("eapps/$DATA_SRC")
                    withQuery(Predicates.eq("code", "912"))
                }
            )
            assertThat(created2.toString()).isEqualTo("eapps/$DATA_SRC@ti-912")
        }

        // 3. Re-apply the same record (same code => same resolved id): must UPDATE, not duplicate.
        val patchId2 = "import-data-upsert-2"
        deployImportDataPatch(
            patchId = patchId2,
            typeRef = TYPE_REF,
            records = listOf(ObjectData.create().set("code", "911").set("gen", "2"))
        )
        waitForApplied(patchId2)

        AuthContext.runAsSystem {
            val matches = records.query(
                RecordsQuery.create {
                    withSourceId("eapps/$DATA_SRC")
                    withQuery(Predicates.eq("code", "911"))
                }
            ).getRecords()
            assertThat(matches)
                .`as`("re-applying the same key must update, not create a duplicate")
                .hasSize(1)

            val gen = records.queryOne(
                RecordsQuery.create {
                    withSourceId("eapps/$DATA_SRC")
                    withQuery(Predicates.eq("code", "911"))
                },
                "gen"
            ).asText()
            assertThat(gen)
                .`as`("the second apply must have updated the existing record in place")
                .isEqualTo("2")
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

    private fun waitForApplied(patchId: String) {
        val start = System.currentTimeMillis()
        while (true) {
            val status = currentPatchStatus(patchId)
            if (status == "APPLIED") {
                return
            }
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
            if (System.currentTimeMillis() - start > 120_000) {
                error("Timeout: patch '$patchId' never reached APPLIED, last status was '$status'")
            }
            Thread.sleep(500)
        }
    }

    /**
     * In-memory records DAO registered at `eapps/test-source`: the data record source used for
     * upsert-by-id on mutate + predicate query, so the executor can create/update imported records
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
        fun testImportUpsertDao(): UpsertRecordsDao = UpsertRecordsDao(DATA_SRC)
    }
}
