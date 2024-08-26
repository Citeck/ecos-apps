package ru.citeck.ecos.apps.domain.artifact.patch

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.artifact.ArtifactService
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.EcosArtifactDto
import ru.citeck.ecos.apps.domain.artifact.artifact.repo.EcosArtifactsRepo
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsDao
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatch
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatchDependsOnApps
import java.util.concurrent.Callable

@Component
@EcosPatch("update-artifact-ext-ids-patch", "2024-08-26T00:00:00Z")
@EcosPatchDependsOnApps(AppName.EMODEL)
class UpdateArtifactsExtIdPatch(
    private val ecosArtifactsService: EcosArtifactsService,
    private val artifactsService: ArtifactService,
    private val ecosArtifactTypesService: EcosArtifactTypesService,
    private val ecosArtifactsRepo: EcosArtifactsRepo,
    private val ecosArtifactsDao: EcosArtifactsDao
) : Callable<String> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun call(): String {

        var currentSkip = 0
        val batchSize = 50
        var patched = 0

        var foundArtifacts = listOf<EcosArtifactDto>()
        val findArtifacts = fun() {
            foundArtifacts = ecosArtifactsService.getAllArtifacts(currentSkip, batchSize)
        }

        findArtifacts()

        while (foundArtifacts.isNotEmpty()) {
            foundArtifacts.forEach { artifact ->
                val currentId = artifact.id
                val artifactType: String = artifact.type

                val artifactData: ByteArray =
                    ecosArtifactsService.getArtifactData(ArtifactRef.create(artifactType, artifact.id))
                        ?: let {
                            log.error { "Artifact data not found by id: $currentId, type: $artifactType" }
                            return@forEach
                        }

                val recitedArtifact = artifactsService.readArtifactFromBytes(artifactType, artifactData)
                val newId = ecosArtifactTypesService.getArtifactMeta(artifactType, recitedArtifact).id

                if (newId != currentId) {
                    log.info { "Update artifact ext id: $currentId -> $newId" }

                    val artifactEntity = ecosArtifactsRepo.getByExtId(artifactType, currentId) ?: let {
                        log.error { "Artifact not found by ext id: $currentId, type: $artifactType" }
                        return@forEach
                    }

                    val entityWithNewId = ecosArtifactsRepo.getByExtId(artifactType, newId)
                    if (entityWithNewId != null) {
                        ecosArtifactsDao.delete(entityWithNewId)
                        log.info { "Artifact with new ext id already exists, delete: $newId, type: $artifactType" }
                    }

                    artifactEntity.extId = newId
                    ecosArtifactsDao.save(artifactEntity)

                    patched++
                }
            }

            currentSkip += batchSize

            findArtifacts()
        }

        log.info {
            "Patched: $patched artifacts"
        }

        return "Patched: $patched"
    }
}
