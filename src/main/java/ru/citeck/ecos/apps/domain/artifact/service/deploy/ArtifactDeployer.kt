package ru.citeck.ecos.apps.domain.artifact.service.deploy

import ru.citeck.ecos.apps.domain.artifact.service.DeployError

interface ArtifactDeployer {

    fun deploy(type: String, artifact: ByteArray): List<DeployError>

    fun getSupportedTypes(): List<String>
}
