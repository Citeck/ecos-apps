package ru.citeck.ecos.apps.domain.patch.service

enum class EcosPatchStatus {
    PENDING,
    IN_PROGRESS,
    DEPS_WAITING,
    FAILED,
    APPLIED
}
