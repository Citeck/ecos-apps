package ru.citeck.ecos.apps.domain.artifact.artifact.service.deploy

import ru.citeck.ecos.apps.app.domain.handler.ArtifactDeployMeta
import ru.citeck.ecos.apps.domain.artifact.artifact.service.DeployError

interface ArtifactDeployer {

    fun deploy(type: String, artifact: ByteArray, meta: ArtifactDeployMeta): List<DeployError>

    fun getSupportedTypes(): List<String>
}
