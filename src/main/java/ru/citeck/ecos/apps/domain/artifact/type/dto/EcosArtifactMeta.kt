package ru.citeck.ecos.apps.domain.artifact.type.dto

import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.Version

data class EcosArtifactMeta(
    val id: String,
    val name: MLText?,
    val dependencies: List<ArtifactRef>,
    val tags: List<String>,
    val typeRevId: Long,
    val modelVersion: Version,
    val system: Boolean
)
