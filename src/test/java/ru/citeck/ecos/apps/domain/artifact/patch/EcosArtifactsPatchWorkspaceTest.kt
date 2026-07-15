package ru.citeck.ecos.apps.domain.artifact.patch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import ru.citeck.ecos.apps.EcosAppsApp
import ru.citeck.ecos.apps.app.domain.artifact.source.AppSourceKey
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType
import ru.citeck.ecos.apps.app.domain.artifact.source.SourceKey
import ru.citeck.ecos.apps.app.domain.artifact.type.ArtifactTypeProvider
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.patch.dto.ArtifactPatchDto
import ru.citeck.ecos.apps.domain.artifact.patch.repo.ArtifactPatchRepo
import ru.citeck.ecos.apps.domain.artifact.patch.repo.ArtifactPatchSyncRepo
import ru.citeck.ecos.apps.domain.artifact.patch.service.EcosArtifactsPatchService
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService
import ru.citeck.ecos.apps.eapps.dto.ArtifactUploadDto
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Instant
import java.util.UUID

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosAppsApp::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class EcosArtifactsPatchWorkspaceTest {

    companion object {
        private const val TYPE_ID = "app/jsontest"
        private const val ADMIN_WORKSPACE = "admin\$workspace"
        private const val ADMIN_MENU_TYPE = "ui/menu"
        private const val ADMIN_MENU_ID = "admin-workspace-menu"
    }

    @Autowired
    lateinit var ecosArtifactsService: EcosArtifactsService

    @Autowired
    lateinit var ecosArtifactsPatchService: EcosArtifactsPatchService

    @Autowired
    lateinit var patchRepo: ArtifactPatchRepo

    @Autowired
    lateinit var patchSyncRepo: ArtifactPatchSyncRepo

    @Autowired
    lateinit var ecosArtifactTypesService: EcosArtifactTypesService

    @Autowired
    lateinit var artifactTypesProvider: ArtifactTypeProvider

    @BeforeEach
    fun setup() {
        val typesDir = artifactTypesProvider.getArtifactTypesDir()
        ecosArtifactTypesService.registerTypes("eapps", typesDir, Instant.now())
    }

    @AfterEach
    fun cleanup() {
        // The class-level @DirtiesContext shares DB state across tests. Wipe rows
        // we own so each test sees only its own data — `findOutOfSyncArtifacts()`
        // queries the whole table and would otherwise pick up siblings' leftovers.
        AuthContext.runAsSystem {
            patchRepo.deleteAll()
            patchSyncRepo.deleteAll()
        }
    }

    @Test
    fun `save patch with explicit workspace persists it`() {
        val patchId = "patch-${UUID.randomUUID()}"
        val ws = "ws-${UUID.randomUUID()}"

        val saved = AuthContext.runAsSystem {
            ecosArtifactsPatchService.save(
                makePatch(patchId, ArtifactRef.create(TYPE_ID, "some-id"), workspace = ws)
            )
        }

        assertThat(saved).isNotNull
        assertThat(saved!!.workspace).isEqualTo(ws)

        val reloaded = ecosArtifactsPatchService.getPatchById(patchId)
        assertThat(reloaded?.workspace).isEqualTo(ws)
    }

    @Test
    fun `legacy admin-workspace-menu patch without workspace auto-fills to admin workspace`() {
        val patchId = "patch-${UUID.randomUUID()}"

        val saved = AuthContext.runAsSystem {
            ecosArtifactsPatchService.save(
                makePatch(patchId, ArtifactRef.create(ADMIN_MENU_TYPE, ADMIN_MENU_ID), workspace = "")
            )
        }

        assertThat(saved?.workspace)
            .`as`("legacy patches without ws field must be pinned to admin\$workspace at save time")
            .isEqualTo(ADMIN_WORKSPACE)
    }

    @Test
    fun `non-admin-menu patch without workspace stays global`() {
        val patchId = "patch-${UUID.randomUUID()}"

        val saved = AuthContext.runAsSystem {
            ecosArtifactsPatchService.save(
                makePatch(patchId, ArtifactRef.create(TYPE_ID, "global-thing"), workspace = "")
            )
        }

        assertThat(saved?.workspace).isEqualTo("")
    }

    @Test
    fun `applyOutOfSyncPatches keeps sync out of sync when target artifact is missing`() {
        val ws = "ws-${UUID.randomUUID()}"
        val artifactId = "missing-${UUID.randomUUID()}"
        val patchId = "patch-${UUID.randomUUID()}"

        // patch references an artifact that doesn't exist in this workspace
        AuthContext.runAsSystem {
            ecosArtifactsPatchService.save(
                makePatch(patchId, ArtifactRef.create(TYPE_ID, artifactId), workspace = ws)
            )

            // Run twice — both passes must keep the sync row out-of-sync (artifact
            // could materialize between attempts; we have no way to know in advance).
            // A regression that "marks in-sync once, then never retries" would pass
            // a single-call check; the second invocation discriminates against that.
            repeat(2) { ecosArtifactsPatchService.applyOutOfSyncPatches() }

            val sync = patchSyncRepo.findByArtifact(TYPE_ID, artifactId, ws)
            assertThat(sync).isNotNull
            assertThat(sync!!.patchLastModified).isGreaterThan(0L)
            assertThat(sync.artifactLastModified)
                .`as`("missing-artifact runs must NOT touch artifactLastModified")
                .isEqualTo(0L)
            assertThat(sync.artifactLastModified)
                .`as`("sync must remain out-of-sync so the next tick retries")
                .isNotEqualTo(sync.patchLastModified)
        }
    }

    @Test
    fun `applyOutOfSyncPatches patches artifact at the right workspace`() {
        val ws = "ws-${UUID.randomUUID()}"
        val artifactId = "patchable-${UUID.randomUUID()}"
        val patchId = "patch-${UUID.randomUUID()}"

        AuthContext.runAsSystem {
            // Upload the artifact in the workspace so the patch has a target.
            val uploaded = ecosArtifactsService.uploadArtifact(
                ArtifactUploadDto(
                    TYPE_ID,
                    ObjectData.create().apply {
                        set("id", artifactId)
                        set("name", "original")
                    },
                    AppSourceKey("test-app", SourceKey("classpath", ArtifactSourceType.APPLICATION)),
                    ws
                )
            )
            assertThat(uploaded).isTrue

            // Save a patch that rewrites the `name` field, scoped to the same workspace.
            ecosArtifactsPatchService.save(
                makePatch(
                    patchId,
                    ArtifactRef.create(TYPE_ID, artifactId),
                    workspace = ws,
                    operations = listOf(
                        ObjectData.create()
                            .set("op", "set")
                            .set("path", "$.name")
                            .set("value", "patched")
                    )
                )
            )

            ecosArtifactsPatchService.applyOutOfSyncPatches()

            val sync = patchSyncRepo.findByArtifact(TYPE_ID, artifactId, ws)
            assertThat(sync).isNotNull
            assertThat(sync!!.artifactLastModified)
                .`as`("successful patch run must mark the sync entity as in-sync")
                .isEqualTo(sync.patchLastModified)
        }
    }

    private fun makePatch(
        id: String,
        target: ArtifactRef,
        workspace: String,
        operations: List<ObjectData> = listOf(
            ObjectData.create()
                .set("op", "set")
                .set("path", "$.name")
                .set("value", "noop")
        )
    ): ArtifactPatchDto {
        val dto = ArtifactPatchDto()
        dto.id = id
        dto.name = MLText(id)
        dto.target = target
        dto.workspace = workspace
        dto.type = "json"
        dto.config = ObjectData.create().set("operations", DataValue.create(operations))
        dto.enabled = true
        return dto
    }
}
