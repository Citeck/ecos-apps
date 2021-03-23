package ru.citeck.ecos.apps.domain.artifact.application.service

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceInfo
import ru.citeck.ecos.apps.domain.artifact.application.job.ApplicationsWatcherJob
import ru.citeck.ecos.apps.eapps.service.ArtifactUpdater

@Component
class EcosArtifactsUpdater(
    private val watcherJob: ApplicationsWatcherJob
) : ArtifactUpdater {

    override fun artifactsForceUpdate(appInstanceId: String, source: ArtifactSourceInfo) {
        watcherJob.forceUpdate(appInstanceId, source)
    }
}
