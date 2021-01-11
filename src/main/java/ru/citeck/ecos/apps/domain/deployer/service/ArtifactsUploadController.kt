package ru.citeck.ecos.apps.domain.deployer.service

import ru.citeck.ecos.apps.domain.artifact.service.upload.ArtifactUploadDto

interface ArtifactsUploadController {

    fun prepareToDeploy(artifacts: List<ArtifactUploadDto>): List<ArtifactUploadDto>
}
