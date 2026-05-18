package ru.citeck.ecos.apps.domain.artifact.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService
import ru.citeck.ecos.apps.eapps.dto.ArtifactUploadDto
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.webapp.lib.spring.context.WorkspaceApiMock
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Instant

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosAppsApp::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class EcosArtifactsWorkspaceManagerPermsTest {

    companion object {
        private const val TYPE_ID = "app/jsontest"
    }

    @Autowired
    lateinit var ecosArtifactsService: EcosArtifactsService
    @Autowired
    lateinit var ecosArtifactTypesService: EcosArtifactTypesService
    @Autowired
    lateinit var artifactTypesProvider: ArtifactTypeProvider
    @Autowired
    lateinit var workspaceApiMock: WorkspaceApiMock

    @BeforeEach
    fun setup() {
        val typesDir = artifactTypesProvider.getArtifactTypesDir()
        ecosArtifactTypesService.registerTypes("eapps", typesDir, Instant.now())
        workspaceApiMock.clear()
        workspaceApiMock.addManager("ws1", "alice")
        workspaceApiMock.addManager("ws2", "bob")
    }

    private fun upload(id: String, ws: String) = ecosArtifactsService.uploadArtifact(
        ArtifactUploadDto(
            TYPE_ID,
            ObjectData.create().apply {
                set("id", id)
                set("name", id)
            },
            AppSourceKey("test", SourceKey("test-source", ArtifactSourceType.USER)),
            ws
        )
    )

    @Test
    fun managerCanUploadInOwnWorkspace() {
        AuthContext.runAs("alice", emptyList()) {
            assertThat(upload("art-1", "ws1")).isTrue
        }
    }

    @Test
    fun managerCannotUploadInOtherWorkspace() {
        AuthContext.runAs("alice", emptyList()) {
            assertThatThrownBy { upload("art-2", "ws2") }
                .isInstanceOf(SecurityException::class.java)
        }
    }

    @Test
    fun managerCannotUploadGlobal() {
        AuthContext.runAs("alice", emptyList()) {
            assertThatThrownBy { upload("art-3", "") }
                .isInstanceOf(SecurityException::class.java)
        }
    }

    @Test
    fun adminCanUploadAnywhere() {
        AuthContext.runAs("admin", listOf(AuthRole.ADMIN)) {
            assertThat(upload("art-admin-1", "")).isTrue
            assertThat(upload("art-admin-2", "ws1")).isTrue
            assertThat(upload("art-admin-3", "ws2")).isTrue
        }
    }
}
