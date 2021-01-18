package ru.citeck.ecos.apps.domain.artifact.artifact.service

import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.domain.artifact.patch.dto.ArtifactPatchDto

interface EcosArtifactsPatchService {

    fun applyPatches(artifact: Any, artifactRef: ArtifactRef, patches: List<ArtifactPatchDto>): Any

    fun getPatches(artifact: ArtifactRef): List<ArtifactPatchDto>
}
