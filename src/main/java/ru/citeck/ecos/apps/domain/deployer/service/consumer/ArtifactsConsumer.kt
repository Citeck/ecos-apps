package ru.citeck.ecos.apps.domain.deployer.service.consumer

import ru.citeck.ecos.commons.io.file.EcosFile

interface ArtifactsConsumer {

    fun deployArtifact(artifact: Any, type: String): List<DeployError>

    fun getArtifactTypesRoot(): EcosFile

    /**
     * if consumer return isValid() == false,
     * then it will be removed from consumers list.
     */
    fun isValid(): Boolean

    fun getId(): String
}
