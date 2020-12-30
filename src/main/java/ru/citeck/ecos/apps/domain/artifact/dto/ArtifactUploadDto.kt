package ru.citeck.ecos.apps.domain.artifact.dto

import ru.citeck.ecos.apps.module.handler.ModuleMeta

data class ArtifactUploadDto(
    val type: String,
    val artifact: Any,
    val ecosApp: String,
    val source: String,
    val sourceType: ArtifactSourceType,
    val patched: Boolean,
    val meta: ModuleMeta
)
