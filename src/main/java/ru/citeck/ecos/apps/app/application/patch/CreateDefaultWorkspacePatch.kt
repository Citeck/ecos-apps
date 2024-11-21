package ru.citeck.ecos.apps.app.application.patch

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.emitter.EmitterConfig
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatch
import java.util.concurrent.Callable

@Component
@EcosPatch("create-default-workspace", "2024-11-21T00:00:00Z", manual = true)
class CreateDefaultWorkspacePatch(
    eventsService: EventsService
) : Callable<Any> {

    companion object {
        private const val EVENT_TYPE = "create-default-workspace"

        private val log = KotlinLogging.logger {}
    }

    private val emitter = eventsService.getEmitter(
        EmitterConfig.create<ObjectData>()
            .withSource("eapps-patch")
            .withEventType(EVENT_TYPE)
            .withEventClass(ObjectData::class.java)
            .build()
    )

    override fun call(): Any {
        log.info { "Emit $EVENT_TYPE event" }
        emitter.emit(ObjectData.create())
        return Unit
    }
}
