package ru.citeck.ecos.apps.domain.artifact.dto

import ru.citeck.ecos.commons.data.MLText

data class EcosArtifact(
    val id: String,
    val data: Any,
    val type: String,
    val name: MLText? = null,
    val tags: List<String>
)
