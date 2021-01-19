package ru.citeck.ecos.apps.domain.artifact.source.service

import ru.citeck.ecos.apps.app.domain.artifact.source.AppSourceKey
import ru.citeck.ecos.commons.io.file.EcosFile
import java.time.Instant

interface AppArtifactsSource {

    fun getArtifacts(typesDir: EcosFile, since: Instant): Map<String, List<Any>>

    fun getLastModified(): Instant

    fun getKey(): AppSourceKey
}
