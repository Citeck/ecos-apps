package ru.citeck.ecos.apps.domain.ecosapp.service

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.domain.artifact.artifact.api.records.EcosArtifactRecords
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.ecosapp.dto.EcosAppDef
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir
import ru.citeck.ecos.commons.utils.NameUtils
import ru.citeck.ecos.commons.utils.ZipUtils
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.io.ByteArrayInputStream

@Component
class EcosAppUtils(
    private val ecosArtifactsService: EcosArtifactsService
) {

    fun writeAppArtifactsToMemDir(appDef: EcosAppDef, memDirId: String, artifactsPath: String): EcosMemDir {
        val artifacts = mutableSetOf<EntityRef>()
        artifacts.addAll(appDef.artifacts)
        artifacts.addAll(appDef.typeRefs.map { typeRefToArtifactRef(it) })

        val rootDir = EcosMemDir(null, NameUtils.escape(memDirId))
        val artifactsDir = rootDir.createDir(artifactsPath)

        for (ref in artifacts) {
            val artifactRef = ArtifactRef.valueOf(ref.getLocalId())
            val artifactRev = ecosArtifactsService.getLastArtifactRev(artifactRef, false)
            if (artifactRev != null) {
                ZipUtils.extractZip(
                    ByteArrayInputStream(artifactRev.data),
                    artifactsDir.getOrCreateDir(artifactRef.type)
                )
            }
        }

        return rootDir
    }

    fun typeRefToArtifactRef(typeRef: EntityRef): EntityRef {
        return EntityRef.create(AppName.EAPPS, EcosArtifactRecords.ID, "model/type$${typeRef.getLocalId()}")
    }

}
