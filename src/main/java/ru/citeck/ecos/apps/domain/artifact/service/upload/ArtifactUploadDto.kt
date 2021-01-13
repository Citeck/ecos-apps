package ru.citeck.ecos.apps.domain.artifact.service.upload

import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactsSourceInfo

data class ArtifactUploadDto(
    val type: String,
    val artifact: Any,
    val source: ArtifactsSourceInfo
)
