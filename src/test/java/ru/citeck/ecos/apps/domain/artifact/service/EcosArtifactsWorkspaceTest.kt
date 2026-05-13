package ru.citeck.ecos.apps.domain.artifact.service

import org.junit.jupiter.api.Assertions.*
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
import ru.citeck.ecos.apps.app.domain.handler.ArtifactDeployMeta
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.DeployStatus
import ru.citeck.ecos.apps.domain.artifact.artifact.repo.EcosArtifactsRepo
import ru.citeck.ecos.apps.domain.artifact.artifact.service.DeployError
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsDao
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.artifact.service.deploy.ArtifactDeployer
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService
import ru.citeck.ecos.apps.domain.ecosapp.repo.EcosAppRepo
import ru.citeck.ecos.apps.eapps.dto.ArtifactUploadDto
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Instant

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosAppsApp::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class EcosArtifactsWorkspaceTest {

    companion object {
        private const val TYPE_ID = "app/jsontest"
    }

    @Autowired
    lateinit var ecosArtifactsService: EcosArtifactsService
    @Autowired
    lateinit var ecosArtifactsDao: EcosArtifactsDao
    @Autowired
    lateinit var ecosArtifactsRepo: EcosArtifactsRepo
    @Autowired
    lateinit var ecosArtifactTypesService: EcosArtifactTypesService
    @Autowired
    lateinit var artifactTypesProvider: ArtifactTypeProvider
    @Autowired
    lateinit var ecosAppRepo: EcosAppRepo
    @Autowired
    lateinit var workspaceService: WorkspaceService

    @BeforeEach
    fun setup() {
        val typesDir = artifactTypesProvider.getArtifactTypesDir()
        ecosArtifactTypesService.registerTypes("eapps", typesDir, Instant.now())
    }

    private fun upload(
        id: String,
        workspace: String = "",
        sourceType: ArtifactSourceType = ArtifactSourceType.USER
    ): Boolean {
        val data = ObjectData.create()
        data["id"] = id
        data["name"] = "Test $id"
        return ecosArtifactsService.uploadArtifact(
            ArtifactUploadDto(
                TYPE_ID,
                data,
                AppSourceKey("test", SourceKey("test-source", sourceType)),
                workspace
            )
        )
    }

    @Test
    fun uploadWithoutWorkspace() {
        upload("ws-test-global")

        val entity = ecosArtifactsRepo.getByExtId(TYPE_ID, "ws-test-global", "")
        assertNotNull(entity, "Global upload must persist an entity with workspace=''")
        assertEquals("", entity!!.workspace)

        val loaded = ecosArtifactsService.getLastArtifact(ArtifactRef.create(TYPE_ID, "ws-test-global"))
        assertNotNull(loaded)
        assertEquals("ws-test-global", loaded!!.id)
        assertEquals("", loaded.workspace)
    }

    @Test
    fun uploadWithWorkspace() {
        upload("ws-test-scoped", "my-workspace")

        val global = ecosArtifactsService.getLastArtifact(ArtifactRef.create(TYPE_ID, "ws-test-scoped"))
        assertNull(global)

        val scopedEntity = ecosArtifactsRepo.getByExtId(TYPE_ID, "ws-test-scoped", "my-workspace")
        assertNotNull(scopedEntity, "Workspace-scoped upload must persist an entity in 'my-workspace'")
        assertEquals("my-workspace", scopedEntity!!.workspace)
    }

    @Test
    fun sameIdDifferentWorkspacesAreDistinct() {
        upload("ws-test-dup", "")
        upload("ws-test-dup", "ws-a")

        val globalArtifact = ecosArtifactsService.getLastArtifact(ArtifactRef.create(TYPE_ID, "ws-test-dup"))
        assertNotNull(globalArtifact)

        val globalEntity = ecosArtifactsRepo.getByExtId(TYPE_ID, "ws-test-dup", "")
        assertNotNull(globalEntity, "Global entity must exist independently")
        val scopedEntity = ecosArtifactsRepo.getByExtId(TYPE_ID, "ws-test-dup", "ws-a")
        assertNotNull(scopedEntity, "Workspace-scoped entity must exist independently")
        assertNotEquals(globalEntity!!.id, scopedEntity!!.id, "Entities must be distinct rows")
    }

    @Test
    fun deployPassesWorkspaceInMeta() {
        upload("ws-test-deploy", "deploy-ws", ArtifactSourceType.APPLICATION)

        val deployedWorkspaces = mutableListOf<String>()
        val deployer = object : ArtifactDeployer {
            override fun deploy(type: String, artifact: ByteArray, meta: ArtifactDeployMeta): List<DeployError> {
                deployedWorkspaces.add(meta.workspace)
                return emptyList()
            }
            override fun getSupportedTypes(): List<String> = listOf(TYPE_ID)
        }

        ecosArtifactsService.deployArtifacts(deployer, Instant.now())

        assertTrue(deployedWorkspaces.contains("deploy-ws"), "Workspace should be passed in deploy meta")
    }

    @Test
    fun uploadFromEcosAppInheritsWorkspace() {
        // ECOS_APP source id carries the workspace as a wsSysId prefix (see EcosAppService.appToSource).
        // resolveUploadWorkspace must decode that prefix so the artifact lands in the same workspace
        // as its parent ecos-app — keeps two same-extId apps in different workspaces independent.
        val sourceId = workspaceService.addWsPrefixToId("inherit-app", "app-ws")

        ecosArtifactsService.uploadArtifact(
            ArtifactUploadDto(
                TYPE_ID,
                ObjectData.create().apply {
                    set("id", "inherited-artifact")
                    set("name", "Inherited")
                },
                AppSourceKey("test", SourceKey(sourceId, ArtifactSourceType.ECOS_APP)),
                ""
            )
        )

        val entity = ecosArtifactsRepo.getByExtId(TYPE_ID, "inherited-artifact", "app-ws")
        assertNotNull(entity, "Artifact should inherit workspace decoded from the ECOS_APP source id")
        assertEquals("app-ws", entity!!.workspace)
        // ecos_app column stores the plain extId, not the composite wsSysId:extId source id
        assertEquals("inherit-app", entity.ecosApp)

        val globalEntity = ecosArtifactsRepo.getByExtId(TYPE_ID, "inherited-artifact", "")
        assertNull(globalEntity, "Artifact should not land in global workspace")
    }

    @Test
    fun explicitWorkspaceWinsOverEcosAppWorkspace() {
        val sourceId = workspaceService.addWsPrefixToId("wins-app", "app-ws")

        ecosArtifactsService.uploadArtifact(
            ArtifactUploadDto(
                TYPE_ID,
                ObjectData.create().apply {
                    set("id", "explicit-wins")
                    set("name", "Explicit")
                },
                AppSourceKey("test", SourceKey(sourceId, ArtifactSourceType.ECOS_APP)),
                "explicit-ws"
            )
        )

        val entity = ecosArtifactsRepo.getByExtId(TYPE_ID, "explicit-wins", "explicit-ws")
        assertNotNull(entity, "Explicit workspace on upload dto must win over the source-id decoding")
    }

    @Test
    fun globalEcosAppArtifactInheritsContentWorkspace() {
        // a *global* ecos-app (no ws prefix in its source id) ships a workspace-scoped artifact
        // by declaring the workspace in the artifact payload — resolveUploadWorkspace must honour it
        ecosArtifactsService.uploadArtifact(
            ArtifactUploadDto(
                TYPE_ID,
                ObjectData.create().apply {
                    set("id", "ws-from-content")
                    set("name", "n")
                    set("workspace", "contracts-ws")
                },
                AppSourceKey("global-app", SourceKey(workspaceService.addWsPrefixToId("global-app", ""), ArtifactSourceType.ECOS_APP)),
                ""
            )
        )
        assertNotNull(
            ecosArtifactsRepo.getByExtId(TYPE_ID, "ws-from-content", "contracts-ws"),
            "a global ecos-app's artifact must inherit the workspace declared in its content"
        )
        assertNull(ecosArtifactsRepo.getByExtId(TYPE_ID, "ws-from-content", ""))
    }

    @Test
    fun ecosAppWorkspaceWinsOverContentWorkspace() {
        ecosArtifactsService.uploadArtifact(
            ArtifactUploadDto(
                TYPE_ID,
                ObjectData.create().apply {
                    set("id", "ws-app-wins")
                    set("name", "n")
                    set("workspace", "content-ws")
                },
                AppSourceKey("scoped-app", SourceKey(workspaceService.addWsPrefixToId("scoped-app", "app-ws"), ArtifactSourceType.ECOS_APP)),
                ""
            )
        )
        assertNotNull(
            ecosArtifactsRepo.getByExtId(TYPE_ID, "ws-app-wins", "app-ws"),
            "the parent ecos-app's workspace wins over the content-declared one"
        )
        assertNull(ecosArtifactsRepo.getByExtId(TYPE_ID, "ws-app-wins", "content-ws"))
    }

    @Test
    fun currentWsPlaceholderInContentResolvesToAppWorkspace() {
        // CURRENT_WS in the content must defer to the deploy workspace (here the global app's "")
        ecosArtifactsService.uploadArtifact(
            ArtifactUploadDto(
                TYPE_ID,
                ObjectData.create().apply {
                    set("id", "ws-current-ph")
                    set("name", "n")
                    set("workspace", "CURRENT_WS")
                },
                AppSourceKey("ph-app", SourceKey(workspaceService.addWsPrefixToId("ph-app", ""), ArtifactSourceType.ECOS_APP)),
                ""
            )
        )
        assertNotNull(
            ecosArtifactsRepo.getByExtId(TYPE_ID, "ws-current-ph", ""),
            "CURRENT_WS must resolve to the deploy workspace, not be stored literally"
        )
        assertNull(ecosArtifactsRepo.getByExtId(TYPE_ID, "ws-current-ph", "CURRENT_WS"))
    }

    @Test
    fun hasUndeployedArtifactsTracksDraftWindow() {
        // Other tests may have left rows in DRAFT, so use the count delta as the
        // unit of comparison — that is order-independent regardless of what's
        // already in the table.
        fun draftCount(): Long = ecosArtifactsRepo
            .countByDeployStatusAndDeletedFalseAndLastModifiedDateLessThanEqual(
                DeployStatus.DRAFT,
                Instant.now()
            )

        val baseline = draftCount()

        upload("ws-undeployed", "ws-a", ArtifactSourceType.APPLICATION)
        val entity = ecosArtifactsRepo.getByExtId(TYPE_ID, "ws-undeployed", "ws-a")
        assertNotNull(entity)

        assertEquals(
            baseline + 1,
            draftCount(),
            "Fresh upload in DRAFT must increase the count by exactly one"
        )
        assertTrue(
            ecosArtifactsService.hasUndeployedArtifacts(entity!!.lastModifiedDate),
            "DRAFT artifact within the window must count as undeployed"
        )

        entity.deployStatus = DeployStatus.DEPLOYED
        ecosArtifactsRepo.save(entity)

        assertEquals(
            baseline,
            draftCount(),
            "Flipping the test artifact to DEPLOYED must drop the count back to baseline"
        )

        // Exercise the public service helper on the negative side too: a future
        // mutation like `return true` in `hasUndeployedArtifacts` would otherwise
        // pass — the count-delta only covers the repo query, not the wrapper.
        if (baseline == 0L) {
            assertFalse(
                ecosArtifactsService.hasUndeployedArtifacts(entity.lastModifiedDate),
                "After flipping to DEPLOYED the gate must clear when nothing else is DRAFT"
            )
        }
    }

    @Test
    fun normalizeWorkspace() {
        assertEquals("", ecosArtifactsDao.normalizeWorkspace(null))
        assertEquals("", ecosArtifactsDao.normalizeWorkspace(""))
        assertEquals("", ecosArtifactsDao.normalizeWorkspace("  "))
        assertEquals("", ecosArtifactsDao.normalizeWorkspace("default"))
        assertEquals("my-ws", ecosArtifactsDao.normalizeWorkspace("my-ws"))
    }
}
