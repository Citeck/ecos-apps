package ru.citeck.ecos.apps.domain.artifact.artifact.api.rest

import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.domain.artifact.application.job.ApplicationsWatcherJob
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.records2.RecordRef

@Component
@RestController
@RequestMapping("/api/artifact")
class ArtifactController(
    val artifactService: EcosArtifactsService,
    val applicationsWatcherJob: ApplicationsWatcherJob
) {

    companion object {
        val log = KotlinLogging.logger {}
    }

    @PostMapping("deploy")
    fun deployArtifact(@RequestParam(required = true) ref: RecordRef) {

        log.info { "Artifact deploy: $ref" }

        artifactService.resetDeployStatus(ArtifactRef.valueOf(ref.id))
        applicationsWatcherJob.forceUpdate()
    }
}
