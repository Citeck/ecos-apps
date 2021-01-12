package ru.citeck.ecos.apps.domain.artifact.service.upload.policy

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactSourceType
import ru.citeck.ecos.apps.domain.artifact.service.upload.ArtifactContext
import ru.citeck.ecos.apps.domain.artifact.service.upload.ArtifactRevContext

@Component
class ApplicationSourcePolicy : ArtifactSourcePolicy {

    override fun isPatchingAllowed(context: ArtifactContext): Boolean {
        return true
    }

    override fun isUploadAllowed(context: ArtifactContext, newRev: ArtifactRevContext): Boolean {

        if (context.getEcosApp().isNotBlank()) {
            return false
        }
        val prevContent = context.getLastRevBySourceType(ArtifactSourceType.APPLICATION)

        return prevContent?.getContentId() != newRev.getContentId()
    }

    override fun getSourceType(): ArtifactSourceType {
        return ArtifactSourceType.APPLICATION
    }
}
