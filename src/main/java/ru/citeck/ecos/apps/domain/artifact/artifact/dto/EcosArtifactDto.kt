package ru.citeck.ecos.apps.domain.artifact.artifact.dto

import ru.citeck.ecos.commons.data.MLText

data class EcosArtifactDto(
    val id: String,
    val data: Any,
    val type: String,
    val name: MLText? = null,
    val tags: List<String>,
    val deployStatus: DeployStatus,
    val source: ArtifactRevSourceInfo,
    val revId: String
)
