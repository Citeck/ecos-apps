package ru.citeck.ecos.apps.domain.artifact.artifact.service.upload.policy

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.ArtifactRevSourceType
import ru.citeck.ecos.apps.domain.artifact.artifact.service.upload.ArtifactContext
import ru.citeck.ecos.apps.domain.artifact.artifact.service.upload.ArtifactRevContext

@Component
class ApplicationSourcePolicy : ArtifactSourcePolicy {

    override fun isUploadAllowed(context: ArtifactContext, newRev: ArtifactRevContext): Boolean {

        if (context.getEcosApp().isNotBlank()) {
            return false
        }
        val prevContent = context.getLastRevBySourceType(ArtifactRevSourceType.APPLICATION)

        return !context.isRevisionsEquals(prevContent, newRev)
    }

    override fun getSourceType(): ArtifactSourceType {
        return ArtifactSourceType.APPLICATION
    }
}
