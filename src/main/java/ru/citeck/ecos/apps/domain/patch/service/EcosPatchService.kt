package ru.citeck.ecos.apps.domain.patch.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import ru.citeck.ecos.apps.domain.artifact.application.job.ApplicationsWatcherJob
import ru.citeck.ecos.apps.domain.patch.config.EcosPatchConfig
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.api.task.scheduler.EcosTaskScheduler
import ru.citeck.ecos.webapp.lib.patch.EcosPatchCommandExecutor
import java.time.Duration
import java.time.Instant
import javax.annotation.PostConstruct

@Service
class EcosPatchService(
    val recordsService: RecordsService,
    val commandsService: CommandsService,
    val scheduler: EcosTaskScheduler,
    val watcherJob: ApplicationsWatcherJob
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private val errorDelayDistribution = listOf(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(10),
            Duration.ofMinutes(30),
            Duration.ofHours(1),
            Duration.ofHours(6),
            Duration.ofHours(12)
        )
    }

    @PostConstruct
    fun init() {
        scheduler.scheduleWithFixedDelay(
            { "Ecos patch task" },
            Duration.ofSeconds(30),
            Duration.ofSeconds(30)
        ) {
            watcherJob.activeApps.forEach {
                applyPatches(it)
            }
        }
    }

    private fun applyPatches(appName: String): Boolean {
        var toApply = 10
        while (toApply > 0 && applyPatch(appName)) {
            toApply--
        }
        return toApply != 10
    }

    private fun applyPatch(appName: String): Boolean {

        val query = RecordsQuery.create {
            withSourceId(EcosPatchConfig.REPO_ID)
            withQuery(Predicates.and(
                Predicates.eq("manual", false),
                Predicates.eq("targetApp", appName),
                Predicates.or(
                    Predicates.and(
                        Predicates.eq("status", EcosPatchStatus.PENDING),
                        // apply only patches changed more than 1 minute ago
                        Predicates.lt(RecordConstants.ATT_MODIFIED, Instant.now().minus(Duration.ofMinutes(1))),
                    ),
                    Predicates.and(
                        Predicates.eq("status", EcosPatchStatus.FAILED),
                        Predicates.and(
                            Predicates.notEmpty("nextTryDate"),
                            Predicates.lt("nextTryDate", Instant.now())
                        )
                    ),
                )
            ))
            withSortBy(SortBy("date", true))
        }
        val patch = recordsService.queryOne(query, EcosPatchEntity::class.java) ?: return false

        log.info { "Found patch for app '${patch.targetApp}' with id '${patch.id}'" }

        val result = commandsService.executeSync {
            withTargetApp(patch.targetApp)
            withBody(EcosPatchCommandExecutor.Command(
                patch.type,
                patch.config
            ))
        }
        val commRes = result.getResultAs(EcosPatchCommandExecutor.CommandRes::class.java)
        var errorMsg = result.primaryError?.message
        if (errorMsg.isNullOrBlank() && commRes == null) {
            errorMsg = "Command result is null. Json: " + result.result
        }
        if (!errorMsg.isNullOrBlank()) {
            patch.errorsCount++
            if (patch.errorsCount > errorDelayDistribution.size) {
                patch.nextTryDate = null
            } else {
                patch.nextTryDate = Instant.now().plus(errorDelayDistribution[patch.errorsCount - 1])
            }
            patch.lastError = errorMsg
            patch.status = EcosPatchStatus.FAILED
            recordsService.mutate(RecordRef.create(EcosPatchConfig.REPO_ID, patch.id), patch)
            log.info { "Patch with id '${patch.id}' for app '${patch.targetApp}' completed with error: $errorMsg" }
        } else {
            patch.errorsCount = 0
            patch.lastError = null
            patch.nextTryDate = null
            patch.status = EcosPatchStatus.APPLIED
            patch.patchResult = DataValue.create(commRes?.result)
            recordsService.mutate(RecordRef.create(EcosPatchConfig.REPO_ID, patch.id), patch)
            log.info { "Patch with id '${patch.id}' for app '${patch.targetApp}' successfully applied" }
        }

        return false
    }
}
