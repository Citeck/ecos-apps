package ru.citeck.ecos.apps.domain.artifact.patch

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.artifact.source.AppSourceKey
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType
import ru.citeck.ecos.apps.app.domain.artifact.source.SourceKey
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.artifact.ArtifactService
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.ArtifactRevSourceType
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.EcosArtifactDto
import ru.citeck.ecos.apps.domain.artifact.artifact.repo.EcosArtifactEntity
import ru.citeck.ecos.apps.domain.artifact.artifact.repo.EcosArtifactRevEntity
import ru.citeck.ecos.apps.domain.artifact.artifact.repo.EcosArtifactsRepo
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsDao
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService
import ru.citeck.ecos.apps.eapps.dto.ArtifactUploadDto
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatch
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatchDependsOnApps
import java.util.concurrent.Callable

@Component
@EcosPatch("update-artifact-ext-ids-patch-2", "2024-08-26T00:00:00Z")
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
                val artifactEntity = ecosArtifactsRepo.getByExtId(artifactType, currentId) ?: let {
                    log.info { "Skip not found or deleted artifact. Ext id: $currentId, type: $artifactType" }
                    return@forEach
                }

                val artifactData: ByteArray =
                    ecosArtifactsService.getArtifactData(ArtifactRef.create(artifactType, currentId))
                        ?: let {
                            log.error { "Artifact data not found by id: $currentId, type: $artifactType" }
                            return@forEach
                        }

                val recitedArtifact = artifactsService.readArtifactFromBytes(artifactType, artifactData)
                val newId = ecosArtifactTypesService.getArtifactMeta(artifactType, recitedArtifact).id

                if (newId != currentId) {
                    log.info { "Update artifact ext id: $currentId -> $newId" }

                    val entityWithNewId = ecosArtifactsRepo.getByExtId(artifactType, newId)
                    if (entityWithNewId != null) {
                        val existsArtifactData: ByteArray =
                            ecosArtifactsService.getArtifactData(ArtifactRef.create(artifactType, newId))
                                ?: let {
                                    log.error { "Artifact data not found by id: $newId, type: $artifactType" }
                                    return@forEach
                                }

                        val existsArtifact = artifactsService.readArtifactFromBytes(artifactType, existsArtifactData)

                        val appSourceKey = getAppSourceKeyForEntity(entityWithNewId) ?: let {
                            log.error {
                                "App source key not found for artifact with new ext id: $newId, type: $artifactType"
                            }
                            return@forEach
                        }

                        log.info {
                            "Artifact with new ext id already exists, upload: $newId, type: $artifactType"
                        }
                        ecosArtifactsService.uploadArtifact(
                            ArtifactUploadDto(
                                artifactType,
                                existsArtifact,
                                appSourceKey
                            )
                        )

                        ecosArtifactsDao.delete(entityWithNewId)
                        log.info { "Artifact deleted: $newId, type: $artifactType" }
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

    private fun getAppSourceKeyForEntity(entity: EcosArtifactEntity): AppSourceKey? {
        val ecosApp = entity.ecosApp ?: ""
        val sourceType = findSourceTypeForEntity(entity) ?: return null
        val lastRev = entity.lastRev ?: return null

        return AppSourceKey(
            ecosApp,
            SourceKey(
                lastRev.sourceId ?: "",
                sourceType
            )
        )
    }

    private fun findSourceTypeForEntity(entity: EcosArtifactEntity): ArtifactSourceType? {
        val lastRev = entity.lastRev
        lastRev.sourceType?.toArtifactSourceType()?.let { return it }

        return findSourceTypeInPreviousRevisions(lastRev.prevRev)
    }

    private fun findSourceTypeInPreviousRevisions(revision: EcosArtifactRevEntity?): ArtifactSourceType? {
        var currentRevision = revision
        while (currentRevision != null) {
            currentRevision.sourceType?.toArtifactSourceType()?.let { return it }
            currentRevision = currentRevision.prevRev
        }
        return null
    }

    private fun ArtifactRevSourceType.toArtifactSourceType(): ArtifactSourceType? {
        return when (this) {
            ArtifactRevSourceType.APPLICATION -> ArtifactSourceType.APPLICATION
            ArtifactRevSourceType.USER -> ArtifactSourceType.USER
            ArtifactRevSourceType.ECOS_APP -> ArtifactSourceType.ECOS_APP
            else -> null
        }
    }
}
