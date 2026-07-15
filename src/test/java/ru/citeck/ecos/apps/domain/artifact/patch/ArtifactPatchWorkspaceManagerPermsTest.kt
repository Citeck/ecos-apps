package ru.citeck.ecos.apps.domain.artifact.patch

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import ru.citeck.ecos.apps.EcosAppsApp
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.domain.artifact.patch.api.records.ArtifactPatchRecordsDao
import ru.citeck.ecos.apps.domain.artifact.patch.dto.ArtifactPatchDto
import ru.citeck.ecos.apps.domain.artifact.patch.service.EcosArtifactsPatchService
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.context.WorkspaceApiMock
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosAppsApp::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class ArtifactPatchWorkspaceManagerPermsTest {

    @Autowired
    lateinit var patchService: EcosArtifactsPatchService
    @Autowired
    lateinit var workspaceApiMock: WorkspaceApiMock
    @Autowired
    lateinit var recordsService: RecordsService

    @BeforeEach
    fun setup() {
        workspaceApiMock.clear()
        workspaceApiMock.addManager("ws1", "alice")
        workspaceApiMock.addManager("ws2", "bob")
    }

    private fun makePatch(id: String, workspace: String): ArtifactPatchDto = ArtifactPatchDto().apply {
        this.id = id
        this.name = MLText(id)
        this.workspace = workspace
        this.target = ArtifactRef.create("app/jsontest", "target-art")
        this.type = "set-value"
        this.config = ObjectData.create()
        this.sourceType = ArtifactSourceType.USER
    }

    @Test
    fun managerCanSavePatchInOwnWorkspace() {
        AuthContext.runAs("alice", emptyList()) {
            assertThat(patchService.save(makePatch("patch-1", "ws1"))).isNotNull
        }
    }

    @Test
    fun managerCannotSavePatchInOtherWorkspace() {
        AuthContext.runAs("alice", emptyList()) {
            assertThatThrownBy { patchService.save(makePatch("patch-2", "ws2")) }
                .isInstanceOf(SecurityException::class.java)
        }
    }

    @Test
    fun managerCannotSaveGlobalPatch() {
        AuthContext.runAs("alice", emptyList()) {
            assertThatThrownBy { patchService.save(makePatch("patch-3", "")) }
                .isInstanceOf(SecurityException::class.java)
        }
    }

    @Test
    fun managerCanDeleteOwnWorkspacePatch() {
        AuthContext.runAs("admin", listOf(AuthRole.ADMIN)) {
            patchService.save(makePatch("patch-4", "ws1"))
        }
        AuthContext.runAs("alice", emptyList()) {
            patchService.delete("patch-4")
        }
        assertThat(patchService.getPatchById("patch-4")).isNull()
    }

    @Test
    fun managerCannotDeleteOtherWorkspacePatch() {
        AuthContext.runAs("admin", listOf(AuthRole.ADMIN)) {
            patchService.save(makePatch("patch-5", "ws2"))
        }
        AuthContext.runAs("alice", emptyList()) {
            assertThatThrownBy { patchService.delete("patch-5") }
                .isInstanceOf(SecurityException::class.java)
        }
    }

    @Test
    fun artifactPatchRecordPermissionsReflectWorkspaceManager() {
        AuthContext.runAs("admin", listOf(AuthRole.ADMIN)) {
            patchService.save(makePatch("perms-patch", "ws1"))
        }

        // ArtifactPatchRecordsDao looks up the patch by plain extId (no ws prefix);
        // the workspace is read from the loaded DTO and used by getPermissions().
        val ref = EntityRef.create(AppName.EAPPS, ArtifactPatchRecordsDao.ID, "perms-patch")

        AuthContext.runAs("alice", emptyList()) {
            val canWrite = recordsService.getAtt(ref, "permissions._has.write?bool").asBoolean()
            assertThat(canWrite).isTrue
        }

        AuthContext.runAs("bob", emptyList()) {
            val canWrite = recordsService.getAtt(ref, "permissions._has.write?bool").asBoolean()
            assertThat(canWrite).isFalse
        }
    }
}
