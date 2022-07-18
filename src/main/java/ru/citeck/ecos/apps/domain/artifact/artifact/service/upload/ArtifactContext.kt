package ru.citeck.ecos.apps.domain.artifact.artifact.service.upload

import ru.citeck.ecos.apps.domain.artifact.artifact.dto.ArtifactRevSourceType

interface ArtifactContext {

    fun getId(): String

    fun getEcosApp(): String

    fun getLastRevBySourceType(type: ArtifactRevSourceType): ArtifactRevContext?

    fun isRevisionsEquals(rev0: ArtifactRevContext?, rev1: ArtifactRevContext?): Boolean

    fun getLastRev(): ArtifactRevContext?
}
