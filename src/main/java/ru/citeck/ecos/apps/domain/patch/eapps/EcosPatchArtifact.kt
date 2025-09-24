package ru.citeck.ecos.apps.domain.patch.eapps

import com.fasterxml.jackson.annotation.JsonIgnore
import ru.citeck.ecos.apps.domain.patch.service.EcosPatchEntity
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.serialization.annotation.IncludeNonDefault
import java.time.Instant

@IncludeNonDefault
data class EcosPatchArtifact(
    val id: String = "",
    val name: MLText = MLText(id),
    val targetApp: String = "",
    val date: Instant = Instant.EPOCH,
    val manual: Boolean = false,
    val dependsOn: List<String> = emptyList(),
    val dependsOnApps: List<String> = emptyList(),
    val type: String = "",
    val config: ObjectData = ObjectData.create()
) {

    fun toEntity(): EcosPatchEntity {
        return EcosPatchEntity(
            patchId = id,
            name = name,
            targetApp = targetApp,
            date = date,
            manual = manual,
            dependsOn = getDependsOnWithApp(),
            dependsOnApps = dependsOnApps,
            type = type,
            config = config.deepCopy()
        )
    }

    @JsonIgnore
    fun getDependsOnWithApp(): List<String> {
        return dependsOn.map {
            if (!it.contains('$')) {
                "$targetApp$$it"
            } else {
                it
            }
        }
    }
}
