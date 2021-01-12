package ru.citeck.ecos.apps.domain.artifact.service.upload.policy

import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactSourceType
import ru.citeck.ecos.apps.domain.artifact.service.upload.ArtifactContext
import ru.citeck.ecos.apps.domain.artifact.service.upload.ArtifactRevContext

interface ArtifactSourcePolicy {

    fun isPatchingAllowed(context: ArtifactContext): Boolean

    fun isUploadAllowed(context: ArtifactContext, newRev: ArtifactRevContext): Boolean

    fun getSourceType(): ArtifactSourceType
}
