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
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatch
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatchDependsOn
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatchDependsOnApps
import java.util.concurrent.Callable

@Component
@EcosPatch("update-artifact-ext-ids-patch-3", "2024-08-26T00:00:00Z")
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


}

@Component
@EcosPatch("fix-notification-artifacts-patch", "2025-06-10T00:00:00Z")
@EcosPatchDependsOn("update-artifact-ext-ids-patch-3")
@EcosPatchDependsOnApps(AppName.EMODEL, AppName.NOTIFICATIONS)
class FixNotificationArtifactsPatch(
    private val ecosArtifactsService: EcosArtifactsService,
    private val ecosArtifactsRepo: EcosArtifactsRepo,
    private val ecosArtifactsDao: EcosArtifactsDao,
    private val recordsService: RecordsService
) : Callable<String> {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val NOTIFICATION_ARTIFACT_TYPE = "notification/template"
        private const val END_WITH_WRONG_POSTFIX = "html.zip"

        private val getValidIdRegex = Regex(".*/([^/]+)\\.html\\.zip")
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

                val artifactType: String = artifact.type
                if (artifactType != NOTIFICATION_ARTIFACT_TYPE) {
                    return@forEach
                }

                val currentId = artifact.id
                if (currentId.endsWith(END_WITH_WRONG_POSTFIX).not()) {
                    return@forEach
                }

                val matcher = getValidIdRegex.find(currentId)
                val validId = matcher?.groupValues?.get(1) ?: let {
                    log.warn { "Invalid artifact id format: $currentId" }
                    return@forEach
                }

                log.info { "Processing notification artifact with ext id: $currentId" }

                val artifactEntity = ecosArtifactsRepo.getByExtId(artifactType, currentId) ?: let {
                    log.info { "Skip not found or deleted artifact. Ext id: $currentId, type: $artifactType" }
                    return@forEach
                }

                val artifactWithValidId = ecosArtifactsRepo.getByExtId(artifactType, validId)
                if (artifactWithValidId != null) {
                    ecosArtifactsDao.delete(artifactWithValidId)
                    log.info { "Deleted artifact with ext id: $validId, type: $artifactType" }
                }

                saveValidIdToEntity(artifactEntity, validId)
                redeployNotification(validId)

                patched++
            }

            currentSkip += batchSize

            findArtifacts()
        }

        log.info {
            "Patched: $patched artifacts"
        }

        return "Patched: $patched"
    }


    private fun saveValidIdToEntity(entity: EcosArtifactEntity, validId: String) {
        val notValidId = entity.extId

        entity.extId = validId
        ecosArtifactsDao.save(entity)
        log.info { "Updated artifact ext id: $notValidId -> $validId" }
    }

    private fun redeployNotification(notificationId: String) {
        val archiveName = "$notificationId.zip"
        val notificationRef = "${AppName.NOTIFICATIONS}/template@$notificationId"

        val notificationDataBase64 = recordsService.getAtt(notificationRef, "data").asText()
        if (notificationDataBase64.isBlank()) {
            log.error { "Notification data not found for ref: $notificationRef" }
            return
        }

        val atts = RecordAtts("${AppName.NOTIFICATIONS}/template@")

        atts[RecordConstants.ATT_WORKSPACE] = "admin\$workspace"
        atts[".att(n:\"_content\"){as(n:\"content-data\"){json}}"] = listOf(
            ObjectData.create()
                .set("storage", "base64")
                .set("name", archiveName)
                .set("url", "data:application/zip;base64,$notificationDataBase64")
                .set("size", notificationDataBase64.length)
                .set("type", "application/zip")
                .set("originalName", archiveName)
        )

        val mutateResult = recordsService.mutate(atts)

        log.info { "Mutate result: $mutateResult" }
    }
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
