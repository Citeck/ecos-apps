package ru.citeck.ecos.apps.domain.patch.service

import java.time.Duration

class EcosPatchProperties(
    val job: EcosPatchJob = EcosPatchJob(),
    val appReadyThresholdDuration: Duration = Duration.ofSeconds(10)
) {

    class EcosPatchJob(
        val initDelayDuration: Duration = Duration.ofSeconds(10),
        val delayDuration: Duration = Duration.ofSeconds(5)
    )
}
