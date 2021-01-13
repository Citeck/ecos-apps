package ru.citeck.ecos.apps.domain.artifact.service

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import ru.citeck.ecos.apps.EcosAppsApp
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourcesProvider
import ru.citeck.ecos.apps.app.domain.artifact.type.ArtifactTypesProvider
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.artifact.ArtifactsService
import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactSourceType
import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactsSourceInfo
import ru.citeck.ecos.apps.domain.artifact.dto.DeployStatus
import ru.citeck.ecos.apps.domain.artifact.service.deploy.ArtifactDeployer
import ru.citeck.ecos.apps.domain.artifact.service.upload.ArtifactUploadDto
import ru.citeck.ecos.apps.domain.artifacttype.service.EcosArtifactTypesService
import ru.citeck.ecos.commons.data.ObjectData
import java.time.Instant

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [EcosAppsApp::class])
class EcosArtifactsServiceTest {

    @Autowired
    lateinit var ecosArtifactsService: EcosArtifactsService
    @Autowired
    lateinit var ecosArtifactTypesService: EcosArtifactTypesService
    @Autowired
    lateinit var artifactTypesProvider: ArtifactTypesProvider
    @Autowired
    lateinit var artifactSourcesProvider: ArtifactSourcesProvider
    @Autowired
    lateinit var artifactsService: ArtifactsService

    @Test
    fun test() {

        val typesDir = artifactTypesProvider.getArtifactTypesDir()
        ecosArtifactTypesService.registerTypes("eapps", typesDir)
        val allTypesCtx = ecosArtifactTypesService.allTypesCtx

        val artifacts = mutableMapOf<String, MutableList<Any>>()
        artifactSourcesProvider.getArtifactSources().forEach {
            artifactSourcesProvider.getArtifacts(it.id, allTypesCtx, Instant.ofEpochMilli(0))
                .forEach { (type, typeArtifacts) ->
                    artifacts.computeIfAbsent(type) { ArrayList() }.addAll(typeArtifacts)
                }
        }

        artifacts.forEach { (type, typeArtifacts) ->
            typeArtifacts.forEach {
                val uploadDto = ArtifactUploadDto(
                    type,
                    it,
                    ArtifactsSourceInfo(
                        "classpath",
                        ArtifactSourceType.APPLICATION
                    )
                )
                ecosArtifactsService.uploadArtifact(uploadDto)
                val meta = ecosArtifactTypesService.getArtifactMeta(type, it)
                println(meta)
                assertEquals(
                    HashSet(meta.dependencies),
                    HashSet(ecosArtifactsService.getDependencies(ArtifactRef.create(type, meta.id)))
                )
            }
        }

        val revIdByArtifact = mutableMapOf<ArtifactRef, String>()

        artifacts.forEach { (type, typeArtifacts) ->

            val artifactsInDb = ecosArtifactsService.getArtifactsByType(type)
            assertEquals(typeArtifacts.size, artifactsInDb.size)
            artifactsInDb.forEach {
                val dependencies = ecosArtifactsService.getDependencies(ArtifactRef.create(type, it.id))
                if (dependencies.isEmpty()) {
                    assertEquals(DeployStatus.DRAFT, it.deployStatus)
                } else {
                    assertEquals(DeployStatus.DEPS_WAITING, it.deployStatus)
                }
                revIdByArtifact[ArtifactRef.create(it.type, it.id)] = it.revId
            }
        }

        artifacts.forEach { (type, typeArtifacts) ->
            typeArtifacts.forEach {
                val uploadDto = ArtifactUploadDto(
                    type,
                    it,
                    ArtifactsSourceInfo(
                        "classpath",
                        ArtifactSourceType.APPLICATION
                    )
                )
                ecosArtifactsService.uploadArtifact(uploadDto)
            }
        }

        artifacts.forEach { (type, typeArtifacts) ->
            val artifactsInDb = ecosArtifactsService.getArtifactsByType(type)
            assertEquals(typeArtifacts.size, artifactsInDb.size)
            artifactsInDb.forEach {
                val ref = ArtifactRef.create(it.type, it.id)
                assertEquals(revIdByArtifact[ref], it.revId)
            }
        }

        val deployedArtifacts = mutableMapOf<String, MutableList<Any>>()

        val deployer = object: ArtifactDeployer {

            override fun deploy(type: String, artifact: ByteArray): List<DeployError> {
                deployedArtifacts.computeIfAbsent(type) { ArrayList() }
                    .add(artifactsService.readArtifactFromBytes(type, artifact))
                return emptyList()
            }

            override fun getSupportedTypes(): List<String> {
                return artifacts.keys.toList()
            }
        }

        var deployIterations = 0
        while (ecosArtifactsService.deployArtifacts(deployer)) {
            if (++deployIterations > 100) {
                error("Unexpected deploy iterations: $deployIterations")
            }
        }

        artifacts.forEach { (type, typeArtifacts) ->

            val artifactsInDb = ecosArtifactsService.getArtifactsByType(type)
            assertEquals(typeArtifacts.size, artifactsInDb.size)
            artifactsInDb.forEach {
                assertEquals(DeployStatus.DEPLOYED, it.deployStatus)
            }
        }

        assertEquals(2, deployIterations)

        deployedArtifacts.forEach { (type, typeArtifacts) ->
            assertEquals(artifacts[type]!!.size, typeArtifacts.size)
            assertEquals(artifacts[type], typeArtifacts)
        }

        val firstArtifact = artifacts["app/jsontest"]?.get(0) as ObjectData
        firstArtifact.set("newField", "newValue")

        ecosArtifactsService.uploadArtifact(ArtifactUploadDto(
            "app/jsontest",
            firstArtifact,
            ArtifactsSourceInfo(
                "test",
                ArtifactSourceType.APPLICATION
            )
        ))

        val artifactRef = ArtifactRef.create("app/jsontest", firstArtifact.get("id").asText());
        val updatedArtifact = ecosArtifactsService.getLastArtifact(artifactRef)!!

        assertFalse(revIdByArtifact[artifactRef].isNullOrBlank())
        assertNotEquals(updatedArtifact.revId, revIdByArtifact[artifactRef])
        assertEquals(DeployStatus.DRAFT, updatedArtifact.deployStatus)

        assertTrue(ecosArtifactsService.deployArtifacts(deployer))
        assertEquals(deployedArtifacts["app/jsontest"]!!.last(), firstArtifact)
    }
}
