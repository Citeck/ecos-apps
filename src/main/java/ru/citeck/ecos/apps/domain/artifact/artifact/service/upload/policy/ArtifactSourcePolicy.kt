package ru.citeck.ecos.apps.domain.artifact.artifact.service.upload.policy

import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType
import ru.citeck.ecos.apps.domain.artifact.artifact.service.upload.ArtifactContext
import ru.citeck.ecos.apps.domain.artifact.artifact.service.upload.ArtifactRevContext

interface ArtifactSourcePolicy {

    fun isPatchingAllowed(context: ArtifactContext): Boolean

    fun isUploadAllowed(context: ArtifactContext, newRev: ArtifactRevContext): Boolean

    fun getSourceType(): ArtifactSourceType
}
