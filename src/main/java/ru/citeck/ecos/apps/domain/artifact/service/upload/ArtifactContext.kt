package ru.citeck.ecos.apps.domain.artifact.service.upload

import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactSourceType

interface ArtifactContext {

    fun getEcosApp(): String

    fun getLastRevBySourceType(type: ArtifactSourceType): ArtifactRevContext?

    fun getLastRev(): ArtifactRevContext?
}
