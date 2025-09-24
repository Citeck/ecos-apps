package ru.citeck.ecos.apps.domain.patch.eapps

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.apps.domain.patch.desc.EcosPatchDesc
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.function.Consumer

@Component
class EcosPatchArtifactHandler(
    val recordsService: RecordsService
) : EcosArtifactHandler<EcosPatchArtifact> {

    override fun deployArtifact(artifact: EcosPatchArtifact) {
        val ref = EntityRef.create(EcosPatchDesc.SRC_ID, "")
        recordsService.mutate(RecordAtts(ref, ObjectData.create(artifact)))
    }

    override fun getArtifactType(): String {
        return "app/patch"
    }

    override fun listenChanges(listener: Consumer<EcosPatchArtifact>) {
    }

    override fun deleteArtifact(artifactId: String) {
        recordsService.delete(EntityRef.create(EcosPatchDesc.SRC_ID, artifactId))
    }
}
