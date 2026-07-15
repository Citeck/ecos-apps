package ru.citeck.ecos.apps.domain.ecosapp

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import ru.citeck.ecos.apps.EcosAppsApp
import ru.citeck.ecos.apps.domain.ecosapp.api.records.EcosAppRecords
import ru.citeck.ecos.apps.domain.ecosapp.dto.EcosAppDef
import ru.citeck.ecos.apps.domain.ecosapp.service.EcosAppService
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.commons.utils.NameUtils
import ru.citeck.ecos.commons.utils.ZipUtils
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.context.WorkspaceApiMock
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosAppsApp::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class EcosAppWorkspaceManagerPermsTest {

    @Autowired
    lateinit var ecosAppService: EcosAppService
    @Autowired
    lateinit var workspaceApiMock: WorkspaceApiMock
    @Autowired
    lateinit var recordsService: RecordsService
    @Autowired
    lateinit var workspaceService: WorkspaceService

    @BeforeEach
    fun setup() {
        workspaceApiMock.clear()
        workspaceApiMock.addManager("ws1", "alice")
        workspaceApiMock.addManager("ws2", "bob")
    }

    private fun saveAs(user: String, roles: List<String>, app: EcosAppDef): () -> Unit = {
        AuthContext.runAs(user, roles) {
            ecosAppService.save(app)
        }
    }

    private fun deleteAs(user: String, roles: List<String>, id: String, ws: String): () -> Unit = {
        AuthContext.runAs(user, roles) {
            ecosAppService.delete(id, ws)
        }
    }

    @Test
    fun managerCanSaveDeleteInOwnWorkspace() {
        AuthContext.runAs("admin", listOf(AuthRole.ADMIN)) {
            ecosAppService.save(
                EcosAppDef.create {
                    withId("app-mgr-1")
                    withWorkspace("ws1")
                }
            )
        }
        saveAs(
            "alice",
            emptyList(),
            EcosAppDef.create {
                withId("app-mgr-1")
                withWorkspace("ws1")
            }
        ).invoke()
        deleteAs("alice", emptyList(), "app-mgr-1", "ws1").invoke()
        assertThat(ecosAppService.getById("app-mgr-1", "ws1")).isNull()
    }

    @Test
    fun managerCannotSaveInOtherWorkspace() {
        assertThatThrownBy(
            saveAs(
                "alice",
                emptyList(),
                EcosAppDef.create {
                    withId("app-mgr-2")
                    withWorkspace("ws2")
                }
            )
        )
            .isInstanceOf(SecurityException::class.java)
    }

    @Test
    fun managerCannotSaveGlobal() {
        assertThatThrownBy(
            saveAs(
                "alice",
                emptyList(),
                EcosAppDef.create {
                    withId("app-mgr-3")
                    withWorkspace("")
                }
            )
        )
            .isInstanceOf(SecurityException::class.java)
    }

    @Test
    fun nonManagerCannotSave() {
        assertThatThrownBy(
            saveAs(
                "charlie",
                emptyList(),
                EcosAppDef.create {
                    withId("app-mgr-4")
                    withWorkspace("ws1")
                }
            )
        )
            .isInstanceOf(SecurityException::class.java)
    }

    @Test
    fun adminCanSaveAnyWorkspace() {
        AuthContext.runAs("admin", listOf(AuthRole.ADMIN)) {
            ecosAppService.save(
                EcosAppDef.create {
                    withId("app-admin-1")
                    withWorkspace("")
                }
            )
            ecosAppService.save(
                EcosAppDef.create {
                    withId("app-admin-2")
                    withWorkspace("ws1")
                }
            )
            ecosAppService.save(
                EcosAppDef.create {
                    withId("app-admin-3")
                    withWorkspace("ws2")
                }
            )
        }
        assertThat(ecosAppService.getById("app-admin-1", "")).isNotNull
        assertThat(ecosAppService.getById("app-admin-2", "ws1")).isNotNull
        assertThat(ecosAppService.getById("app-admin-3", "ws2")).isNotNull
    }

    @Test
    fun managerCanUploadZipInOwnWorkspace() {
        val zipBytes = buildMinimalAppZip("zip-mgr-1")

        AuthContext.runAs("alice", emptyList()) {
            val saved = ecosAppService.uploadZip(zipBytes, "ws1")
            assertThat(saved).isNotNull
            assertThat(saved.id).isEqualTo("zip-mgr-1")
            assertThat(saved.workspace).isEqualTo("ws1")
        }
    }

    @Test
    fun managerCannotUploadZipInOtherWorkspace() {
        val zipBytes = buildMinimalAppZip("zip-mgr-2")

        AuthContext.runAs("alice", emptyList()) {
            assertThatThrownBy { ecosAppService.uploadZip(zipBytes, "ws2") }
                .isInstanceOf(SecurityException::class.java)
        }
    }

    @Test
    fun ecosAppRecordPermissionsReflectWorkspaceManager() {
        AuthContext.runAs("admin", listOf(AuthRole.ADMIN)) {
            ecosAppService.save(
                EcosAppDef.create {
                    withId("perms-1")
                    withWorkspace("ws1")
                }
            )
        }

        val refId = workspaceService.addWsPrefixToId("perms-1", "ws1")
        val ref = EntityRef.create(AppName.EAPPS, EcosAppRecords.ID, refId)

        AuthContext.runAs("alice", emptyList()) {
            val canWrite = recordsService.getAtt(ref, "permissions._has.write?bool").asBoolean()
            assertThat(canWrite).isTrue
        }

        AuthContext.runAs("bob", emptyList()) {
            val canWrite = recordsService.getAtt(ref, "permissions._has.write?bool").asBoolean()
            assertThat(canWrite).isFalse
        }
    }

    private fun buildMinimalAppZip(appId: String): ByteArray {
        val rootDir = EcosMemDir(null, NameUtils.escape(appId))
        val meta = EcosAppDef.create {
            withId(appId)
            withName(MLText(appId))
        }
        rootDir.createFile("meta.json", Json.mapper.toPrettyString(meta) ?: "")
        return ZipUtils.writeZipAsBytes(rootDir)
    }
}
