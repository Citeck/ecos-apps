package ru.citeck.ecos.apps.domain.ecosapp.api.rest

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import ru.citeck.ecos.apps.domain.artifact.application.job.ApplicationsWatcherJob
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.ArtifactRevSourceType
import ru.citeck.ecos.apps.domain.artifact.artifact.repo.EcosArtifactRevEntity
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsDao
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.ecosapp.service.EcosAppService
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
@RestController
@RequestMapping("/api/ecosapp")
class EcosAppController(
    val artifactService: EcosArtifactsService,
    val artifactsDao: EcosArtifactsDao,
    val ecosAppsService: EcosAppService,
    val applicationsWatcherJob: ApplicationsWatcherJob,
    val workspaceService: WorkspaceService
) {

    companion object {
        val log = KotlinLogging.logger {}
    }

    @Transactional
    @PostMapping("ecos-app-full-delete")
    fun ecosAppFullDelete(
        @RequestBody(required = true) body: EcosAppFullDeleteBody
    ): String {
        if (AuthContext.isNotRunAsSystemOrAdmin()) {
            error("Access denied")
        }
        val appsToDeleteIds = LinkedHashSet<String>()
        body.ecosAppId?.forEach {
            if (it.isNotBlank()) {
                appsToDeleteIds.add(it)
            }
        }
        body.ecosAppRef?.forEach {
            if (it.getLocalId().isNotBlank()) {
                appsToDeleteIds.add(it.getLocalId())
            }
        }
        log.info { "Try to delete ECOS applications $appsToDeleteIds by ${AuthContext.getCurrentUser()}" }

        if (appsToDeleteIds.isEmpty()) {
            return "{\"status\": \"OK\"}"
        }
        for (appToDeleteId in appsToDeleteIds) {
            val idInWs = workspaceService.convertToIdInWs(appToDeleteId)
            val artifactsByApp = artifactsDao.getArtifactsByEcosApp(idInWs.id, idInWs.workspace)
            log.info { "Delete $appToDeleteId. Artifacts: ${artifactsByApp.size}" }
            val resetCondition: (EcosArtifactRevEntity) -> Boolean = {
                (it.sourceType == ArtifactRevSourceType.ECOS_APP && it.sourceId == idInWs.id) ||
                    it.sourceType == ArtifactRevSourceType.USER
            }
            for (artifact in artifactsByApp) {
                val artifactRef = artifactsDao.toArtifactRef(artifact).toString()
                val resetRes = try {
                    artifactService.resetRevision(artifact, resetCondition)
                } catch (e: Throwable) {
                    log.error(e) { "Error while resetting artifact $artifactRef" }
                    false
                }
                val msg = "Artifact $artifactRef " + if (resetRes) {
                    "has been reset."
                } else {
                    "has not been reset."
                }
                log.info { msg }
            }
            log.info { "All artifacts have been reset for $appToDeleteId" }
            ecosAppsService.delete(idInWs.id, idInWs.workspace)
        }
        log.info { "All specified applications was deleted: $appsToDeleteIds" }
        applicationsWatcherJob.forceUpdate()
        return "{\"status\": \"OK\"}"
    }

    class EcosAppFullDeleteBody(
        val ecosAppRef: List<EntityRef>? = null,
        val ecosAppId: List<String>? = null
    )
}
