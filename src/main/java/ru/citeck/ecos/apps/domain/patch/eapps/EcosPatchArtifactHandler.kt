package ru.citeck.ecos.apps.domain.patch.eapps

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.apps.domain.patch.desc.EcosPatchDesc
import ru.citeck.ecos.apps.domain.patch.service.EcosPatchEntity
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.function.Consumer

@Component
class EcosPatchArtifactHandler(
    val recordsService: RecordsService
) : EcosArtifactHandler<EcosPatchArtifact> {

    override fun deployArtifact(artifact: EcosPatchArtifact) {

        val existingConfig = recordsService.queryOne(
            RecordsQuery.create {
                withSourceId(EcosPatchDesc.SRC_ID)
                withQuery(
                    Predicates.and(
                        Predicates.eq("patchId", artifact.id),
                        Predicates.eq("targetApp", artifact.targetApp)
                    )
                )
            },
            EcosPatchEntity::class.java
        )

        val entity = if (existingConfig != null) {
            artifact.toEntity(existingConfig)
            existingConfig
        } else {
            artifact.toEntity()
        }

        val ref = EntityRef.create(EcosPatchDesc.SRC_ID, entity.id)
        AuthContext.runAsSystem {
            recordsService.mutate(RecordAtts(ref, ObjectData.create(entity)))
        }
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
