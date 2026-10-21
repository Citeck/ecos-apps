package ru.citeck.ecos.apps.domain.ecosapp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import ru.citeck.ecos.apps.EcosAppsApp
import ru.citeck.ecos.apps.app.domain.artifact.type.ArtifactTypeProvider
import ru.citeck.ecos.apps.app.domain.handler.ArtifactDeployMeta
import ru.citeck.ecos.apps.artifact.ArtifactService
import ru.citeck.ecos.apps.domain.artifact.application.job.ApplicationsWatcherJob
import ru.citeck.ecos.apps.domain.artifact.artifact.repo.EcosArtifactsRepo
import ru.citeck.ecos.apps.domain.artifact.artifact.service.DeployError
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.artifact.service.deploy.ArtifactDeployer
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService
import ru.citeck.ecos.apps.domain.ecosapp.dto.EcosAppDef
import ru.citeck.ecos.apps.domain.ecosapp.service.EcosAppService
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.commons.utils.ZipUtils
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Instant

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosAppsApp::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class EcosAppCoDeployedWorkspaceTest {

    @Autowired
    lateinit var ecosAppService: EcosAppService

    @Autowired
    lateinit var artifactsService: ArtifactService

    @Autowired
    lateinit var artifactTypesProvider: ArtifactTypeProvider

    @Autowired
    lateinit var artifactTypesService: EcosArtifactTypesService

    @Autowired
    lateinit var ecosArtifactsService: EcosArtifactsService

    @Autowired
    lateinit var artifactsRepo: EcosArtifactsRepo

    @Autowired
    lateinit var watcherJob: ApplicationsWatcherJob

    @Test
    fun `global app ZIP exported and imported into workspace carries co-deployed sibling references`() {
        val type = "app/jsontest"
        val appId = "co-deployed-zip-app"
        val workspace = "co-deployed-zip-target"
        val ids = listOf("co-deployed-first", "co-deployed-second")
        artifactTypesService.registerTypes("eapps", artifactTypesProvider.getArtifactTypesDir(), Instant.now())
        val root = EcosMemDir()
        val meta = EcosAppDef.create {
            withId(appId)
            withArtifacts(ids.map { EntityRef.valueOf("eapps/artifact@$type${'$'}$it") })
        }
        root.createFile("meta.json", requireNotNull(Json.mapper.toPrettyString(meta)))
        artifactsService.writeArtifacts(
            root.createDir("artifacts"),
            mapOf(type to ids.map { ObjectData.create().set("id", it).set("reference", "co-deployed-second") })
        )

        AuthContext.runAsSystem {
            ecosAppService.uploadZip(ZipUtils.writeZipAsBytes(root), "")
            repeat(2) { watcherJob.forceUpdateSync() }
            for (id in ids) {
                val source = requireNotNull(artifactsRepo.getByExtId(type, id, ""))
                assertThat(source.ecosApp).isEqualTo(appId)
                assertThat(source.workspace).isEmpty()
                assertThat(source.lastRev).isNotNull()
            }

            val exported = ecosAppService.getAppData(appId, "")
            val exportedRoot = ZipUtils.extractZip(exported)
            val exportedMeta = requireNotNull(Json.mapper.read(exportedRoot.getFile("meta.json"), EcosAppDef::class.java))
            assertThat(exportedMeta.workspace).isEmpty()
            assertThat(Json.mapper.toString(exportedMeta)).doesNotContain("CURRENT_WS:")
            val exportedArtifacts = artifactsService.readArtifacts(
                requireNotNull(exportedRoot.getDir("artifacts")),
                artifactTypesService.allTypesCtx.map { it.getTypeContext() }
            )
            val exportedIds = requireNotNull(exportedArtifacts[type]).map {
                requireNotNull(Json.mapper.convert(it, ObjectData::class.java))["id"].asText()
            }
            assertThat(exportedIds).containsExactlyInAnyOrderElementsOf(ids)

            val imported = ecosAppService.uploadZip(exported, workspace)
            assertThat(imported.id).isEqualTo(appId)
            assertThat(imported.workspace).isEqualTo(workspace)
            repeat(2) { watcherJob.forceUpdateSync() }
            for (id in ids) {
                val target = requireNotNull(artifactsRepo.getByExtId(type, id, workspace))
                assertThat(target.ecosApp).isEqualTo(appId)
                assertThat(target.workspace).isEqualTo(workspace)
                assertThat(target.lastRev).isNotNull()
                assertThat(requireNotNull(artifactsRepo.getByExtId(type, id, "")).workspace).isEmpty()
            }

            val deployed = mutableMapOf<String, ArtifactDeployMeta>()
            val deployer = object : ArtifactDeployer {
                override fun getSupportedTypes(): List<String> {
                    return listOf(type)
                }

                override fun deploy(artifactType: String, artifact: ByteArray, deployMeta: ArtifactDeployMeta): List<DeployError> {
                    if (deployMeta.workspace == workspace) {
                        val data = requireNotNull(
                            Json.mapper.convert(artifactsService.readArtifactFromBytes(artifactType, artifact), ObjectData::class.java)
                        )
                        assertThat(data["reference"].asText()).isEqualTo("co-deployed-second")
                        deployed[data["id"].asText()] = deployMeta
                    }
                    return emptyList()
                }
            }
            ecosArtifactsService.deployArtifacts(deployer, Instant.now())
            assertThat(deployed.keys).containsExactlyInAnyOrderElementsOf(ids)
            val siblings = ids.map { EntityRef.valueOf("eapps/test-source@$it") }
            for (deployMeta in deployed.values) {
                assertThat(deployMeta.sourceType).isEqualTo("ECOS_APP")
                assertThat(deployMeta.sourceId).isEqualTo(appId)
                assertThat(deployMeta.coDeployedArtifacts).containsExactlyInAnyOrderElementsOf(siblings)
            }
        }
    }
}
