package ru.citeck.ecos.apps.domain.artifact.service.upload

import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactSourceInfo

data class ArtifactUploadDto(
    val type: String,
    val artifact: Any,
    val source: ArtifactSourceInfo
)
