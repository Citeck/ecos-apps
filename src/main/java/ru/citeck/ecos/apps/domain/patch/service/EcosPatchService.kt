package ru.citeck.ecos.apps.domain.patch.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import ru.citeck.ecos.apps.domain.artifact.application.job.ApplicationsWatcherJob
import ru.citeck.ecos.apps.domain.patch.config.EcosPatchConfig
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records2.predicate.model.ValuePredicate
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
            Duration.ofMinutes(1),
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(10),
            Duration.ofMinutes(30),
            Duration.ofHours(1),
            Duration.ofHours(2),
            Duration.ofHours(3)
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
            log.trace { "Apply patches for apps: $apps" }
            apps.forEach {
                applyPatches(it, apps)
            }
        }
    }

    private fun applyPatches(appName: String, availableApps: Set<String>): Boolean {
        var toApply = 10
        while (toApply > 0 && applyPatch(appName, availableApps)) {
            toApply--
        }
        return toApply != 10
    }

    private fun applyPatch(appName: String, availableApps: Set<String>): Boolean {

        if (!isAppReadyToDeployPatches(appName)) {
            log.trace { "App is not ready yet: $appName" }
            return false
        }

        val query = RecordsQuery.create {
            withSourceId(EcosPatchConfig.REPO_ID)
            withQuery(
                Predicates.and(
                    Predicates.eq("manual", false),
                    Predicates.eq("targetApp", appName),
                    Predicates.or(
                        Predicates.empty("dependsOnApps"),
                        ValuePredicate.contains("dependsOnApps", availableApps),
                    ),
                    Predicates.or(
                        Predicates.eq("status", EcosPatchStatus.PENDING),
                        Predicates.and(
                            Predicates.or(
                                Predicates.eq("status", EcosPatchStatus.FAILED),
                                Predicates.eq("status", EcosPatchStatus.IN_PROGRESS)
                            ),
                            Predicates.and(
                                Predicates.notEmpty("nextExecDate"),
                                Predicates.lt("nextExecDate", Instant.now())
                            )
                        ),
                    )
                )
            )
            withSortBy(SortBy("date", true))
        }
        val patch = recordsService.queryOne(query, EcosPatchEntity::class.java)
        if (patch == null) {
            log.trace { "Active patches is not found for app: '$appName'" }
            return false
        }

        if (isAnyPatchNotApplied(patch.dependsOn)) {
            patch.status = EcosPatchStatus.DEPS_WAITING
            recordsService.mutate(RecordRef.create(EcosPatchConfig.REPO_ID, patch.id), patch)
            return false
        }

        val patchId = "${patch.targetApp}$${patch.patchId}"

        log.info { "Found patch '$patchId'. Execute it" }

        val result = commandsService.executeSync {
            withTargetApp(patch.targetApp)
            withBody(
                EcosPatchCommandExecutor.Command(
                    patch.type,
                    patch.config,
                    patch.state
                )
            )
            withTtl(Duration.ofMinutes(10))
        }

        log.info { "Patch command completed. Patch: $patchId" }

        val commRes = result.getResultAs(EcosPatchCommandExecutor.CommandRes::class.java)
        var errorMsg = result.primaryError?.message
        if (errorMsg.isNullOrBlank() && commRes == null) {
            errorMsg = "Command result is null. Json: " + result.result
        }
        if (!errorMsg.isNullOrBlank()) {
            patch.errorsCount++
            if (patch.errorsCount > errorDelayDistribution.size) {
                patch.nextExecDate = null
            } else {
                patch.nextExecDate = Instant.now().plus(errorDelayDistribution[patch.errorsCount - 1])
            }
            patch.lastError = errorMsg
            patch.status = EcosPatchStatus.FAILED
            recordsService.mutate(RecordRef.create(EcosPatchConfig.REPO_ID, patch.id), patch)
            log.info { "Patch '$patchId' completed with error: $errorMsg" }
        } else {
            patch.state = commRes?.result?.state ?: ObjectData.create()
            patch.errorsCount = 0
            patch.lastError = null
            patch.status = if (commRes?.result?.completed == true) {
                patch.nextExecDate = null
                EcosPatchStatus.APPLIED
            } else {
                patch.nextExecDate = commRes?.result?.nextExecutionTime
                EcosPatchStatus.IN_PROGRESS
            }
            patch.patchResult = DataValue.create(commRes?.result)
            recordsService.mutate(RecordRef.create(EcosPatchConfig.REPO_ID, patch.id), patch)
            log.info {
                val msg = "Patch '$patchId' "
                if (patch.status == EcosPatchStatus.APPLIED) {
                    msg + "successfully applied"
                } else {
                    msg + "partially applied"
                }
            }

            if (patch.status == EcosPatchStatus.APPLIED) {
                val depsWaitingPatches = recordsService.query(
                    RecordsQuery.create {
                        withSourceId(EcosPatchConfig.REPO_ID)
                        withQuery(
                            Predicates.and(
                                Predicates.eq("status", EcosPatchStatus.DEPS_WAITING),
                                Predicates.contains("dependsOn", patch.targetApp + "$" + patch.patchId)
                            )
                        )
                    },
                    EcosPatchEntity::class.java
                )
                depsWaitingPatches.getRecords().forEach { depsWaitingPatch ->
                    if (!isAnyPatchNotApplied(depsWaitingPatch.dependsOn)) {
                        depsWaitingPatch.status = EcosPatchStatus.PENDING
                        recordsService.mutate(
                            RecordRef.create(EcosPatchConfig.REPO_ID, depsWaitingPatch.id),
                            depsWaitingPatch
                        )
                    }
                }
            }
        }

        return false
    }

    private fun isAppReadyToDeployPatches(appName: String): Boolean {
        val query = RecordsQuery.create {
            withSourceId(EcosPatchConfig.REPO_ID)
            withQuery(
                Predicates.and(
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
                )
            )
            withMaxItems(1)
        }
        return recordsService.query(query).getRecords().isEmpty()
    }

    private fun isAnyPatchNotApplied(patches: List<String>): Boolean {
        if (patches.isEmpty()) {
            return false
        }
        val patchIdPredicates = patches.mapNotNull {
            val appAndId = it.split("$")
            if (appAndId.size == 2) {
                Predicates.and(
                    Predicates.eq("targetApp", appAndId[0]),
                    Predicates.eq("patchId", appAndId[1])
                )
            } else {
                null
            }
        }
        if (patchIdPredicates.isEmpty()) {
            return false
        }
        return recordsService.query(
            RecordsQuery.create {
                withSourceId(EcosPatchConfig.REPO_ID)
                withQuery(
                    Predicates.and(
                        Predicates.eq("status", EcosPatchStatus.APPLIED),
                        Predicates.or(patchIdPredicates)
                    )
                )
            }
        ).getRecords().size < patchIdPredicates.size
    }
}
