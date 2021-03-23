package ru.citeck.ecos.apps.domain.artifact.type.service

import ru.citeck.ecos.apps.artifact.type.TypeContext
import ru.citeck.ecos.apps.artifact.type.TypeMeta

class EcosArtifactTypeContextImpl(
    private val typeRevId: Long,
    private val typeContext: TypeContext
) : EcosArtifactTypeContext {

    override fun getTypeRevId(): Long {
        return typeRevId
    }

    override fun getId(): String {
        return typeContext.getId()
    }

    override fun getMeta(): TypeMeta {
        return typeContext.getMeta()
    }

    override fun getTypeContext(): TypeContext {
        return typeContext
    }
}
