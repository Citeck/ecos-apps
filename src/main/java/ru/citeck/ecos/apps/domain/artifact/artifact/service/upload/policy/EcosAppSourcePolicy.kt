package ru.citeck.ecos.apps.domain.artifact.artifact.service.upload.policy

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.ArtifactRevSourceType
import ru.citeck.ecos.apps.domain.artifact.artifact.service.upload.ArtifactContext
import ru.citeck.ecos.apps.domain.artifact.artifact.service.upload.ArtifactRevContext

@Component
class EcosAppSourcePolicy : ArtifactSourcePolicy {

    companion object {
        val log = KotlinLogging.logger {}
    }

    override fun isUploadAllowed(context: ArtifactContext, newRev: ArtifactRevContext): Boolean {

        if (context.getEcosApp().isBlank()) {
            return true
        }
        if (newRev.getSourceId() != context.getEcosApp()) {
            log.warn {
                "Artifact owned by '" + context.getEcosApp() + "' app and can't " +
                    "be updated by " + newRev.getSourceType() + " '" + newRev.getSourceId() + "'"
            }
            return false
        }
        val prevContent = context.getLastRevBySourceType(
            ArtifactRevSourceType.ECOS_APP,
            ArtifactRevSourceType.APPLICATION
        )

        return !context.isRevisionsEquals(prevContent, newRev)
    }

    override fun getSourceType(): ArtifactSourceType {
        return ArtifactSourceType.ECOS_APP
    }
}
