package ru.citeck.ecos.apps.domain.patch.service

import java.time.Duration

class EcosPatchProperties(
    val job: EcosPatchJob = EcosPatchJob(),
    val appReadyThresholdDuration: Duration = Duration.ofSeconds(10),
    // Quiet period after the last artifact deploy before the patch job reconciles DEPS_WAITING
    // patches, so a burst of deploys triggers a single reconcile instead of one per deploy.
    val deploySettleDuration: Duration = Duration.ofSeconds(10)
) {

    class EcosPatchJob(
        val delayDuration: Duration = Duration.ofSeconds(5)
    )
}
