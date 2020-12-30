package ru.citeck.ecos.apps.domain.deployer.service

import ru.citeck.ecos.apps.module.type.TypeContext

interface ArtifactsSource {

    fun getArtifacts(types: List<TypeContext>): Map<String, List<Any>>

    fun isValid(): Boolean

    fun getId(): String
}
