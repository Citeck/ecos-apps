package ru.citeck.ecos.apps.domain.artifact.artifact.service

import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType

class EcosArtifactToPatch(
    val artifact: Any,
    val hasPatchedRev: Boolean,
    val sourceType: ArtifactSourceType
)
