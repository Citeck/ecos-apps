package ru.citeck.ecos.apps.domain.patch.service

data class PatchBatchConfig(
    val field: String = "",
    val size: Int = DEFAULT_SIZE
) {
    companion object {
        const val DEFAULT_SIZE = 20
    }
}
