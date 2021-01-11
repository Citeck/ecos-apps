package ru.citeck.ecos.apps.domain.artifact.service.upload

import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactSourceType

data class ArtifactUploadDto(

    val type: String,

    val artifact: Any,
    val patchedArtifact: Any,

    val sourceId: String,
    val sourceType: ArtifactSourceType
)
