package ru.citeck.ecos.apps.domain.patch.eapps

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.apps.domain.patch.config.EcosPatchConfig
import ru.citeck.ecos.apps.domain.patch.service.EcosPatchEntity
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import java.util.function.Consumer

@Component
class EcosPatchArtifactHandler(
    val recordsService: RecordsService
) : EcosArtifactHandler<EcosPatchArtifact> {

    override fun deployArtifact(artifact: EcosPatchArtifact) {

        val existingConfig = recordsService.queryOne(
            RecordsQuery.create {
                withSourceId(EcosPatchConfig.REPO_ID)
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

        val ref = RecordRef.create(EcosPatchConfig.REPO_ID, entity.id)
        recordsService.mutate(RecordAtts(ref, ObjectData.create(entity)))
    }

    override fun getArtifactType(): String {
        return "app/patch"
    }

    override fun listenChanges(listener: Consumer<EcosPatchArtifact>) {
    }

    override fun deleteArtifact(artifactId: String) {
        recordsService.delete(RecordRef.create(EcosPatchConfig.REPO_ID, artifactId))
    }
}
