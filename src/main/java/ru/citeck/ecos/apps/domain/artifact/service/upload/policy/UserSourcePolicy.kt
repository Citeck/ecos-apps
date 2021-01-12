package ru.citeck.ecos.apps.domain.artifact.service.upload.policy

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactSourceType
import ru.citeck.ecos.apps.domain.artifact.service.upload.ArtifactContext
import ru.citeck.ecos.apps.domain.artifact.service.upload.ArtifactRevContext

@Component
class UserSourcePolicy : ArtifactSourcePolicy {

    override fun isPatchingAllowed(context: ArtifactContext): Boolean {
        return false
    }

    override fun isUploadAllowed(context: ArtifactContext, newRev: ArtifactRevContext): Boolean {
        return true
    }

    override fun getSourceType(): ArtifactSourceType {
        return ArtifactSourceType.USER
    }
}
