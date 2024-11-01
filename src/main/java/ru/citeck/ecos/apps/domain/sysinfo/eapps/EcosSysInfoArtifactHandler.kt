package ru.citeck.ecos.apps.domain.sysinfo.eapps

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.apps.domain.sysinfo.config.EcosSystemInfoConfig.Companion.SYS_INFO_REPO_ID
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.function.Consumer

@Component
class EcosSysInfoArtifactHandler(
    private val records: RecordsService,
    private val events: EventsService
) : EcosArtifactHandler<DataValue> {

    override fun deployArtifact(artifact: DataValue) {
        records.mutate(EntityRef.create(SYS_INFO_REPO_ID, ""), artifact)
    }

    override fun getArtifactType(): String {
        return "app/system-info"
    }

    override fun listenChanges(listener: Consumer<DataValue>) {
    }

    override fun deleteArtifact(artifactId: String) {
        records.delete(EntityRef.create(SYS_INFO_REPO_ID, artifactId))
    }
}
