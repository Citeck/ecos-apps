package ru.citeck.ecos.apps.domain.patch.eapps

import ru.citeck.ecos.apps.domain.patch.service.EcosPatchEntity
import ru.citeck.ecos.apps.domain.patch.service.EcosPatchStatus
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import java.time.Instant

data class EcosPatchArtifact(
    val id: String,
    val name: MLText = MLText(id),
    val targetApp: String,
    val date: Instant,
    val manual: Boolean = false,
    val dependsOn: List<String> = emptyList(),
    val dependsOnApps: List<String> = emptyList(),
    val type: String,
    val config: ObjectData
) {

    fun toEntity(): EcosPatchEntity {
        return EcosPatchEntity(
            patchId = id,
            name = name,
            targetApp = targetApp,
            date = Instant.now(),
            manual = manual,
            dependsOn = getDependsOnWithApp(),
            dependsOnApps = dependsOnApps,
            type = type,
            config = config.deepCopy()
        )
    }

    fun toEntity(entity: EcosPatchEntity) {

        if (entity.date.isBefore(date)) {
            entity.status = EcosPatchStatus.PENDING
        }

        entity.name = name
        entity.date = date
        entity.manual = manual
        entity.type = type
        entity.config = config.deepCopy()
        entity.dependsOn = getDependsOnWithApp()
        entity.dependsOnApps = dependsOnApps
    }

    private fun getDependsOnWithApp(): List<String> {
        return dependsOn.map {
            if (!it.contains('$')) {
                "$targetApp$$it"
            } else {
                it
            }
        }
    }
}
