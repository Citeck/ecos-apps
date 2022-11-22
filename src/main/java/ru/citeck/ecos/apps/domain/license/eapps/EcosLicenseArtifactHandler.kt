package ru.citeck.ecos.apps.domain.license.eapps

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.apps.domain.license.service.LicensesZkProviderInitializer
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.listener.ListenerConfig
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.events2.type.RecordCreatedEvent
import ru.citeck.ecos.license.EcosLicenseInstance
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.function.Consumer

@Component
class EcosLicenseArtifactHandler(
    private val records: RecordsService,
    private val events: EventsService
) : EcosArtifactHandler<DataValue> {

    override fun deployArtifact(artifact: DataValue) {
        records.mutate(EntityRef.create(LicensesZkProviderInitializer.LIC_SRC_ID, ""), artifact)
    }

    override fun getArtifactType(): String {
        return "app/license"
    }

    override fun listenChanges(listener: Consumer<DataValue>) {
        listOf(RecordChangedEvent.TYPE, RecordCreatedEvent.TYPE).forEach { eventType ->
            events.addListener(ListenerConfig.create<RecordEventAtts> {
                withDataClass(RecordEventAtts::class.java)
                withEventType(eventType)
                withAction { event ->
                    val jsonNode = Json.mapper.toNonDefaultJson(event.json)
                    listener.accept(DataValue.create(jsonNode))
                }
                withLocal(true)
                withFilter(Predicates.eq("typeDef.id", "ecos-license"))
            })
        }
    }

    override fun deleteArtifact(artifactId: String) {
        records.delete(EntityRef.create(LicensesZkProviderInitializer.LIC_SRC_ID, artifactId))
    }

    class RecordEventAtts(
        @AttName("record${ScalarType.JSON_SCHEMA}")
        val json: EcosLicenseInstance
    )
}
