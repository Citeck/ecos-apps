package ru.citeck.ecos.apps.domain.ecosapp.service

import ru.citeck.ecos.apps.domain.artifact.artifact.api.records.EcosArtifactRecords
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef

object ArtifactUtils {

    fun typeRefToArtifactRef(typeRef: EntityRef): EntityRef {
        return EntityRef.create(AppName.EAPPS, EcosArtifactRecords.ID, "model/type$${typeRef.getLocalId()}")
    }
}
