package ru.citeck.ecos.apps.domain.deployer.job

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.domain.deployer.service.loader.ArtifactsLoader

@Component
class SyncNewSourcesAndConsumers(
    private val artifactsLoader: ArtifactsLoader
) {

    companion object {
        private const val SYNC_PERIOD = 2_000L
    }

    @Scheduled(fixedDelay = SYNC_PERIOD, initialDelay = 10_000)
    fun execute() {

        val lastChanged = artifactsLoader.getLastChangedMs()

        if (System.currentTimeMillis() - lastChanged < SYNC_PERIOD) {
            // wait until next sync if new source or consumer was registered after last sync
            return
        }

        artifactsLoader.syncNewSourcesAndConsumers()
    }
}
