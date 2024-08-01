package ru.citeck.ecos.apps.domain.artifact.artifact.api.rest

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.domain.artifact.application.job.ApplicationsWatcherJob
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.AllUserRevisionsResetStatus
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.commons.utils.ZipUtils
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.OffsetDateTime

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
    fun deployArtifact(@RequestParam(required = true) ref: EntityRef) {
        artifactService.resetDeployStatus(ArtifactRef.valueOf(ref.getLocalId()))
        applicationsWatcherJob.forceUpdate()
    }

    @PostMapping("reset-user-rev")
    fun resetUserRevision(@RequestParam(required = true) ref: EntityRef) {
        artifactService.resetUserRevision(ArtifactRef.valueOf(ref.getLocalId()))
        applicationsWatcherJob.forceUpdate()
    }

    @PostMapping("reset-all-user-revs", produces = [MediaType.APPLICATION_JSON_UTF8_VALUE])
    fun resetAllUserRevisions(): AllUserRevisionsResetStatus {
        val status = artifactService.resetAllUserRevisions()
        applicationsWatcherJob.forceUpdate()
        return status
    }

    @GetMapping("download-revisions")
    fun downloadRevisions(
        @RequestParam(required = true) ref: EntityRef,
        @RequestParam(required = true) fromTime: String
    ): HttpEntity<ByteArray> {

        val artifactRef = ArtifactRef.valueOf(ref.getLocalId())
        val fromTimeInstant = OffsetDateTime.parse(fromTime).toInstant()

        val dir = artifactService.getArtifactRevisions(artifactRef, fromTimeInstant)
        val resultBytes = ZipUtils.writeZipAsBytes(dir)

        val headers = HttpHeaders()
        headers.contentDisposition = ContentDisposition.builder("attachment")
            .filename(artifactRef.id.replace("[^a-zA-Z\\-\\d_]".toRegex(), "_") + ".zip")
            .build()

        return HttpEntity(resultBytes, headers)
    }
}
