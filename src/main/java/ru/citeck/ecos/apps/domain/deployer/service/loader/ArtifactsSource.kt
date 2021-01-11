package ru.citeck.ecos.apps.domain.deployer.service.loader

import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactSourceType
import ru.citeck.ecos.commons.io.file.EcosFile

interface ArtifactsSource {

    fun getArtifacts(typesDir: EcosFile): Map<String, List<Any>>

    fun getSourceType(): ArtifactSourceType

    fun getId(): String
}
