package ru.citeck.ecos.apps.domain.artifact.service

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import ru.citeck.ecos.apps.EcosAppsApp
import ru.citeck.ecos.apps.app.domain.artifact.source.*
import ru.citeck.ecos.apps.app.domain.artifact.type.ArtifactTypeProvider
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.artifact.ArtifactService
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.DeployStatus
import ru.citeck.ecos.apps.domain.artifact.artifact.service.DeployError
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.artifact.service.deploy.ArtifactDeployer
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService
import ru.citeck.ecos.apps.eapps.dto.ArtifactUploadDto
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Instant

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosAppsApp::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class EcosArtifactsServiceTest {

    @Autowired
    lateinit var ecosArtifactsService: EcosArtifactsService
    @Autowired
    lateinit var ecosArtifactTypesService: EcosArtifactTypesService
    @Autowired
    lateinit var artifactTypesProvider: ArtifactTypeProvider

    @Autowired
    @Qualifier("defaultArtifactSourceProvider")
    lateinit var artifactSourcesProvider: ArtifactSourceProvider

    @Autowired
    lateinit var artifactsService: ArtifactService

    @Test
    fun test() {

        val typesDir = artifactTypesProvider.getArtifactTypesDir()
        ecosArtifactTypesService.registerTypes("eapps", typesDir, Instant.now())
        val allTypesCtx = ecosArtifactTypesService.allTypesCtx.map { it.getTypeContext() }

        val artifacts = mutableMapOf<String, MutableList<Any>>()
        artifactSourcesProvider.getArtifactSources().forEach {
            artifactSourcesProvider.getArtifacts(it.key, allTypesCtx, Instant.ofEpochMilli(0))
                .forEach { (type, typeArtifacts) ->
                    artifacts.computeIfAbsent(type) { ArrayList() }.addAll(typeArtifacts)
                }
        }

        artifacts.forEach { (type, typeArtifacts) ->
            typeArtifacts.forEach {
                val uploadDto = ArtifactUploadDto(
                    type,
                    it,
                    AppSourceKey("eapps", SourceKey("classpath", ArtifactSourceType.APPLICATION))
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
                    AppSourceKey("eapps", SourceKey("classpath", ArtifactSourceType.APPLICATION))
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

        val deployer = object : ArtifactDeployer {

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
            Assertions.assertThat(typeArtifacts).containsExactlyInAnyOrderElementsOf(artifacts[type])
        }

        val jsonTestTypeId = "app/jsontest"
        val firstArtifact = artifacts[jsonTestTypeId]?.get(0) as ObjectData
        firstArtifact.set("newField", "newValue")

        ecosArtifactsService.uploadArtifact(
            ArtifactUploadDto(
                jsonTestTypeId,
                firstArtifact,
                AppSourceKey("eapps", SourceKey("classpath", ArtifactSourceType.APPLICATION))
            )
        )

        val artifactRef = ArtifactRef.create(jsonTestTypeId, firstArtifact.get("id").asText())
        val updatedArtifact = ecosArtifactsService.getLastArtifact(artifactRef)!!

        assertFalse(revIdByArtifact[artifactRef].isNullOrBlank())
        assertNotEquals(updatedArtifact.revId, revIdByArtifact[artifactRef])
        assertEquals(DeployStatus.DRAFT, updatedArtifact.deployStatus)

        assertTrue(ecosArtifactsService.deployArtifacts(deployer))
        assertEquals(deployedArtifacts[jsonTestTypeId]!!.last(), firstArtifact)
    }
}
