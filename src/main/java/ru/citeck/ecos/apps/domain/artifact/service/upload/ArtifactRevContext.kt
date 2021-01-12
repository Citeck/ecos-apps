package ru.citeck.ecos.apps.domain.artifact.service.upload

import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactSourceType

interface ArtifactRevContext {

    fun getContentId(): Long

    fun getSourceType(): ArtifactSourceType

    fun getSourceId(): String
}
