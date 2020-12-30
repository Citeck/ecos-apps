package ru.citeck.ecos.apps.domain.deployer.service

import ru.citeck.ecos.apps.module.handler.ModuleWithMeta
import ru.citeck.ecos.apps.module.type.TypeContext

interface ArtifactsConsumer {

    fun deployArtifact(artifact: Any, type: String)

    fun getArtifactsWithMeta(artifacts: List<Any>): List<ModuleWithMeta<Any>>

    fun prepareToDeploy(type: TypeContext, artifacts: List<Any>): List<ModuleWithMeta<Any>>

    fun getConsumedTypes(): List<TypeContext>

    /**
     * if consumer return isValid() == false,
     * then it will be removed from consumers list.
     */
    fun isValid(): Boolean

    fun getId(): String
}
