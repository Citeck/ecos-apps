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
import ru.citeck.ecos.webapp.lib.patch.EcosPatchCommandExecutor
import ru.citeck.ecos.webapp.lib.spring.context.task.EcosTasksManager
import java.time.Duration
import java.time.Instant
import javax.annotation.PostConstruct

@Service
class EcosPatchService(
    val recordsService: RecordsService,
    val commandsService: CommandsService,
    val tasksManager: EcosTasksManager,
    val watcherJob: ApplicationsWatcherJob,
    val properties: EcosPatchProperties
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val SCHEDULER_ID = "ecos-patches"

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
        tasksManager.getScheduler(SCHEDULER_ID).scheduleWithFixedDelay(
            { "Ecos patch task" },
            properties.job.initDelayDuration,
            properties.job.delayDuration
        ) {
            val apps = watcherJob.activeApps
            log.debug { "Apply patches for apps: $apps" }
            apps.forEach {
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

        if (!isAppReadyToDeployPatches(appName)) {
            log.debug { "App is not ready yet: $appName" }
            return false
        }

        val query = RecordsQuery.create {
            withSourceId(EcosPatchConfig.REPO_ID)
            withQuery(Predicates.and(
                Predicates.eq("manual", false),
                Predicates.eq("targetApp", appName),
                Predicates.or(
                    Predicates.eq("status", EcosPatchStatus.PENDING),
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
        val patch = recordsService.queryOne(query, EcosPatchEntity::class.java)
        if (patch == null) {
            log.debug { "Active patches is not found for app: '$appName'" }
            return false
        }

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

    private fun isAppReadyToDeployPatches(appName: String): Boolean {
        val query = RecordsQuery.create {
            withSourceId(EcosPatchConfig.REPO_ID)
            withQuery(Predicates.and(
                Predicates.eq("manual", false),
                Predicates.eq("targetApp", appName),
                Predicates.and(
                    Predicates.eq("status", EcosPatchStatus.PENDING),
                    // apply only patches for app with last change more than 30 seconds ago
                    // this delay required to collect patches for app from all sources
                    Predicates.gt(
                        RecordConstants.ATT_MODIFIED,
                        Instant.now().minus(properties.appReadyThresholdDuration)
                    ),
                )
            ))
            withMaxItems(1)
        }
        return recordsService.query(query).getRecords().isEmpty()
    }
}
