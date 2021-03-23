package ru.citeck.ecos.apps.domain.artifact.artifact.dto

/**
 * Warning! Ordinal used in DB. Do not change values order!
 *
 * Should be superset of ArtifactSourceType
 */
enum class ArtifactRevSourceType {
    USER,
    ECOS_APP,
    APPLICATION,
    PATCH
}
