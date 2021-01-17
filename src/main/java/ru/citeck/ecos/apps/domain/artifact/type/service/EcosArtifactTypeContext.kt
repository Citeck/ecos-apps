package ru.citeck.ecos.apps.domain.artifact.type.service

import ru.citeck.ecos.apps.artifact.type.TypeContext
import ru.citeck.ecos.apps.artifact.type.TypeMeta

interface EcosArtifactTypeContext {

    fun getId(): String

    fun getTypeRevId(): Long

    fun getMeta(): TypeMeta

    fun getTypeContext(): TypeContext
}
