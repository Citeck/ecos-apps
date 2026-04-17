package ru.citeck.ecos.apps.domain.ecosapp.eapps

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.WsAwareArtifactHandler
import ru.citeck.ecos.apps.domain.ecosapp.service.EcosAppService
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir
import ru.citeck.ecos.commons.utils.ZipUtils
import java.util.function.BiConsumer

@Component
class EcosAppArtifactHandler(
    val ecosAppService: EcosAppService
) : WsAwareArtifactHandler<EcosAppArtifactHandler.EcosAppArtifact> {

    override fun deployArtifact(artifact: EcosAppArtifact, workspace: String) {

        val ecosAppTargetDir = EcosMemDir()

        val targetArtifactsDir = ecosAppTargetDir.createDir("artifacts")
        val sourceArtifactsDir = ZipUtils.extractZip(artifact.artifactsDir)
        targetArtifactsDir.copyFilesFrom(sourceArtifactsDir)

        ecosAppTargetDir.createFile("meta.json", artifact.metaContent)

        ecosAppService.uploadZip(ZipUtils.writeZipAsBytes(ecosAppTargetDir), workspace)
    }

    override fun getArtifactType(): String {
        return "app/ecosapp"
    }

    override fun listenChanges(listener: BiConsumer<EcosAppArtifact, String>) {
        // do nothing
    }

    override fun deleteArtifact(artifactId: String, workspace: String) {
        ecosAppService.delete(artifactId, workspace)
    }

    class EcosAppArtifact(
        val metaContent: ByteArray,
        val artifactsDir: ByteArray
    )
}
