package ru.citeck.ecos.apps.domain.artifact.service.source

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactsSourceInfo
import ru.citeck.ecos.apps.domain.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.service.upload.ArtifactUploadDto
import ru.citeck.ecos.apps.domain.artifacttype.service.EcosArtifactTypesService
import ru.citeck.ecos.apps.domain.deployer.service.loader.ArtifactsSource
import ru.citeck.ecos.commons.io.file.EcosFile
import java.util.concurrent.ConcurrentHashMap

@Component
class ArtifactsSources(
    private val ecosArtifactsService: EcosArtifactsService,
    private val ecosArtifactTypesService: EcosArtifactTypesService
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val sources: MutableMap<String, ArtifactsSource> = ConcurrentHashMap()

    fun addSource(source: ArtifactsSource) {
        sources[source.getId()] = source
        uploadArtifacts(source, ecosArtifactTypesService.allTypesDir)
    }

    fun uploadArtifacts(typesDir: EcosFile) {
        uploadArtifacts(sources.values, typesDir)
    }

    private fun uploadArtifacts(sources: Collection<ArtifactsSource>, typesDir: EcosFile) {
        sources.forEach { uploadArtifacts(it, typesDir) }
    }

    private fun uploadArtifacts(source: ArtifactsSource, typesDir: EcosFile) {

        sources[source.getId()] = source

        val sourceInfo = ArtifactsSourceInfo(
            source.getId(),
            source.getSourceType()
        )

        log.info { "Added new artifacts source: $sourceInfo" }

        val artifacts: Map<String, List<Any>> = try {
            source.getArtifacts(typesDir)
        } catch (e: Exception) {
            log.error(e) { "Artifacts can't be received from source: $sourceInfo" }
            emptyMap()
        }

        artifacts.forEach { (type, typeArtifacts) ->

            for (typeArtifact in typeArtifacts) {
                try {
                    ecosArtifactsService.uploadArtifact(ArtifactUploadDto(type, typeArtifact, sourceInfo))
                } catch (e: Exception) {
                    log.error(e) {
                        "Artifact uploading failed. Source: $sourceInfo Type: $type Artifact: $typeArtifact"
                    }
                }
            }
        }
    }
}
