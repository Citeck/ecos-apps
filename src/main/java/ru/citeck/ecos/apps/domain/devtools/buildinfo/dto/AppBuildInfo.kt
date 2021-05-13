package ru.citeck.ecos.apps.domain.devtools.buildinfo.dto

import ru.citeck.ecos.commons.data.ObjectData

data class AppBuildInfo(
    val id: String,
    val label: String,
    val description: String,
    val info: ObjectData
)
