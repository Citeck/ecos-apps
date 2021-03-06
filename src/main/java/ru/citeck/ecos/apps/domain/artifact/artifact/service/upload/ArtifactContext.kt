package ru.citeck.ecos.apps.domain.artifact.artifact.service.upload

import ru.citeck.ecos.apps.domain.artifact.artifact.dto.ArtifactRevSourceType

interface ArtifactContext {

    fun getEcosApp(): String

    fun getLastRevBySourceType(type: ArtifactRevSourceType): ArtifactRevContext?

    fun getLastRev(): ArtifactRevContext?
}
