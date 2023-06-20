package ru.citeck.ecos.apps.domain.artifact.artifact.dto

import ru.citeck.ecos.commons.data.MLText
import java.time.Instant

data class EcosArtifactDto(
    val id: String,
    val data: Any,
    val type: String,
    val name: MLText? = null,
    val tags: List<String>,
    val deployStatus: DeployStatus,
    val source: ArtifactRevSourceInfo,
    val ecosApp: String,
    val system: Boolean,
    val revId: String,
    val modified: Instant?,
    val created: Instant?
)
