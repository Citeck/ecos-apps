package ru.citeck.ecos.apps.domain.artifact.source.service

import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType
import ru.citeck.ecos.commons.io.file.EcosFile
import java.time.Instant

interface ArtifactsSource {

    fun getArtifacts(typesDir: EcosFile, since: Instant): Map<String, List<Any>>

    fun getLastModified(): Instant

    fun getSourceType(): ArtifactSourceType

    fun getId(): String

    fun getAppName(): String
}
