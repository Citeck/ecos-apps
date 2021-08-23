package ru.citeck.ecos.apps.domain.ecosapp.service

import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.artifact.reader.ArtifactsReader
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceInfo
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceProvider
import ru.citeck.ecos.apps.app.domain.artifact.source.SourceKey
import ru.citeck.ecos.apps.artifact.type.TypeContext
import java.time.Instant

@Component
class EcosAppArtifactSourceProvider(
    @Lazy
    val ecosAppService: EcosAppService
) : ArtifactSourceProvider {

    private lateinit var reader: ArtifactsReader

    override fun init(reader: ArtifactsReader) {
        this.reader = reader
    }

    override fun getArtifactSources(): List<ArtifactSourceInfo> {
        return ecosAppService.getArtifactSources()
    }

    override fun getArtifacts(source: SourceKey, types: List<TypeContext>, since: Instant): Map<String, List<Any>> {
        val artifactsDir = ecosAppService.getArtifactsDir(source, types, since)
        return reader.readArtifacts(artifactsDir, types)
    }

    override fun isStatic(): Boolean {
        return false
    }

    override fun listenChanges(listener: (ArtifactSourceInfo) -> Unit) {
    }
}
