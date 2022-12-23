package ru.citeck.ecos.apps.domain.artifact.patch.eapp

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType
import ru.citeck.ecos.apps.app.domain.handler.ArtifactDeployMeta
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.apps.domain.artifact.patch.dto.ArtifactPatchDto
import ru.citeck.ecos.apps.domain.artifact.patch.service.EcosArtifactsPatchService
import ru.citeck.ecos.commons.utils.StringUtils
import java.util.function.Consumer

@Component
class ArtifactPatchArtifactHandler(
    private val service: EcosArtifactsPatchService
) : EcosArtifactHandler<ArtifactPatchDto> {

    override fun deployArtifact(artifact: ArtifactPatchDto) {
        val sourceType = ArtifactDeployMeta.getThreadMeta().sourceType
        if (StringUtils.isNotBlank(sourceType)) {
            artifact.sourceType = ArtifactSourceType.valueOf(sourceType)
        } else {
            artifact.sourceType = null
        }
        service.save(artifact)
    }

    override fun getArtifactType(): String {
        return "app/artifact-patch"
    }

    override fun deleteArtifact(artifactId: String) {
        service.delete(artifactId)
    }

    override fun listenChanges(listener: Consumer<ArtifactPatchDto>) {
        service.addListener { dto ->
            if (dto != null) {
                listener.accept(dto)
            }
        }
    }
}
