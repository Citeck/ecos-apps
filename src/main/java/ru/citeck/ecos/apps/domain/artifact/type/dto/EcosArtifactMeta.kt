package ru.citeck.ecos.apps.domain.artifact.type.dto

import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.commons.data.MLText

data class EcosArtifactMeta(
    val id: String,
    val name: MLText?,
    val dependencies: List<ArtifactRef>,
    val tags: List<String>
)
