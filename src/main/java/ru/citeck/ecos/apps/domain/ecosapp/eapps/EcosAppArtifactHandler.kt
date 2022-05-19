package ru.citeck.ecos.apps.domain.ecosapp.eapps

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.apps.domain.ecosapp.service.EcosAppService
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir
import ru.citeck.ecos.commons.utils.ZipUtils
import java.util.function.Consumer

@Component
class EcosAppArtifactHandler(
    val ecosAppService: EcosAppService
) : EcosArtifactHandler<EcosAppArtifactHandler.EcosAppArtifact> {

    override fun deployArtifact(artifact: EcosAppArtifact) {

        val ecosAppTargetDir = EcosMemDir()

        val targetArtifactsDir = ecosAppTargetDir.createDir("artifacts")
        val sourceArtifactsDir = ZipUtils.extractZip(artifact.artifactsDir)
        targetArtifactsDir.copyFilesFrom(sourceArtifactsDir)

        ecosAppTargetDir.createFile("meta.json", artifact.metaContent)

        ecosAppService.uploadZip(ZipUtils.writeZipAsBytes(ecosAppTargetDir))
    }

    override fun getArtifactType(): String {
        return "app/ecosapp"
    }

    override fun listenChanges(listener: Consumer<EcosAppArtifact>) {
        // do nothing
    }

    override fun deleteArtifact(artifactId: String) {
        ecosAppService.delete(artifactId)
    }

    class EcosAppArtifact(
        val metaContent: ByteArray,
        val artifactsDir: ByteArray
    )
}
