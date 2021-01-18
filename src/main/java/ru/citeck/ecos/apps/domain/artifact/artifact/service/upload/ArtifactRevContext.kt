package ru.citeck.ecos.apps.domain.artifact.artifact.service.upload

import ru.citeck.ecos.apps.domain.artifact.artifact.dto.ArtifactRevSourceType

interface ArtifactRevContext {

    fun getContentId(): Long

    fun getSourceType(): ArtifactRevSourceType

    fun getSourceId(): String
}
