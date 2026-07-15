package ru.citeck.ecos.apps.domain.patch.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.DeployStatus
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class PatchRefsDeployChecker(
    private val ecosArtifactTypesService: EcosArtifactTypesService,
    private val ecosArtifactsService: EcosArtifactsService
) {

    companion object {
        private val log = KotlinLogging.logger {}

        fun resolveRefs(config: ObjectData, paths: List<String>): List<EntityRef> {
            val result = ArrayList<EntityRef>()
            for (path in paths) {
                var dv: DataValue = DataValue.create(config)
                for (part in path.split(".")) {
                    dv = dv[part]
                }
                collectRefs(dv, result)
            }
            return result
        }

        private fun collectRefs(dv: DataValue, out: MutableList<EntityRef>) {
            if (dv.isArray()) {
                for (i in 0 until dv.size()) {
                    collectRefs(dv[i], out)
                }
            } else {
                val text = dv.asText()
                if (text.isNotBlank()) {
                    out.add(EntityRef.valueOf(text))
                }
            }
        }
    }

    /**
     * True if every ref maps to an artifactType and that artifact is DEPLOYED in eapps.
     */
    fun allDeployed(refs: List<EntityRef>): Boolean {
        for (ref in refs) {
            val artifactType = ecosArtifactTypesService.getTypeIdForRecordRef(ref)
            if (artifactType.isBlank()) {
                // sourceId not mapped to an artifactType — treat as not-yet-available and wait
                log.warn { "Patch dependsOnRefs: cannot map ref '$ref' to an artifactType; waiting" }
                return false
            }
            val dto = ecosArtifactsService.getLastArtifact(ArtifactRef.create(artifactType, ref.getLocalId()))
            if (dto == null || dto.deployStatus != DeployStatus.DEPLOYED) {
                return false
            }
        }
        return true
    }
}
