package ru.citeck.ecos.apps.domain.artifact.artifact.service

data class DeployError(
    val type: String,
    val message: String,
    val stackTrace: List<String> = emptyList()
)
