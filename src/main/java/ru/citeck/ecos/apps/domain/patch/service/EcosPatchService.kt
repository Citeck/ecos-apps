package ru.citeck.ecos.apps.domain.patch.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import ru.citeck.ecos.apps.domain.artifact.application.job.ApplicationsWatcherJob
import ru.citeck.ecos.apps.domain.patch.desc.EcosPatchDesc
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.task.schedule.Schedules
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records2.predicate.model.ValuePredicate
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.lock.LockContext
import ru.citeck.ecos.webapp.api.task.EcosTasksApi
import ru.citeck.ecos.webapp.lib.lock.EcosAppLockService
import ru.citeck.ecos.webapp.lib.patch.EcosPatchCommandExecutor
import ru.citeck.ecos.webapp.lib.patch.EcosPatchService
import java.time.Duration
import java.time.Instant
import kotlin.reflect.jvm.jvmName

@Service
class EcosPatchService(
    val recordsService: RecordsService,
    val commandsService: CommandsService,
    val ecosTasksApi: EcosTasksApi,
    val ecosWebAppApi: EcosWebAppApi,
    val watcherJob: ApplicationsWatcherJob,
    val properties: EcosPatchProperties,
    val ecosAppLockService: EcosAppLockService
) {

    companion object {

        private const val SCHEDULER_ID = "ecos-patches"
        private val ECOS_PATCHES_LOCK_KEY = EcosPatchService::class.jvmName + "-$SCHEDULER_ID-lock"

        private val log = KotlinLogging.logger {}

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
        ecosWebAppApi.doWhenAppReady {
            ecosTasksApi.getScheduler(SCHEDULER_ID).schedule(
                "Ecos patch task",
                Schedules.fixedDelay(properties.job.delayDuration)
            ) {
                ecosAppLockService.doInSyncOrSkip(ECOS_PATCHES_LOCK_KEY) { lockCtx ->
                    val apps = watcherJob.activeApps
                    log.trace { "Apply patches for apps: $apps" }
                    apps.forEach { applyPatches(it, lockCtx) }
                }
            }
        }
    }

    private fun applyPatches(appName: String, lockCtx: LockContext) {
        var iterationsLimit = 1000
        while (lockCtx.isLocked() && iterationsLimit > 0) {
            val availableApps = watcherJob.activeApps
            if (!availableApps.contains(appName)) {
                break
            }
            if (!applyPatch(appName, availableApps)) {
                break
            }
            iterationsLimit--
        }
    }

    private fun applyPatch(appName: String, availableApps: Set<String>): Boolean {

        if (!isAppReadyToDeployPatches(appName)) {
            log.trace { "App is not ready yet: $appName" }
            return false
        }

        val query = RecordsQuery.create {
            withSourceId(EcosPatchDesc.SRC_ID)
            withQuery(
                Predicates.and(
                    Predicates.eq(EcosPatchDesc.ATT_MANUAL, false),
                    Predicates.eq(EcosPatchDesc.ATT_TARGET_APP, appName),
                    Predicates.or(
                        Predicates.empty(EcosPatchDesc.ATT_DEPENDS_ON_APPS),
                        ValuePredicate.contains(EcosPatchDesc.ATT_DEPENDS_ON_APPS, availableApps),
                    ),
                    Predicates.or(
                        Predicates.eq(EcosPatchDesc.ATT_STATUS, EcosPatchStatus.PENDING),
                        Predicates.and(
                            Predicates.or(
                                Predicates.eq(EcosPatchDesc.ATT_STATUS, EcosPatchStatus.FAILED),
                                Predicates.eq(EcosPatchDesc.ATT_STATUS, EcosPatchStatus.IN_PROGRESS)
                            ),
                            Predicates.and(
                                Predicates.notEmpty(EcosPatchDesc.ATT_NEXT_EXEC_DATE),
                                Predicates.lt(EcosPatchDesc.ATT_NEXT_EXEC_DATE, Instant.now())
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
            recordsService.mutate(EntityRef.create(EcosPatchDesc.SRC_ID, patch.id), patch)
            return true
        }

        applyPatch(patch)

        return true
    }

    fun applyPatch(id: String) {
        val patch = recordsService.getAtts(EcosPatchDesc.getRef(id), EcosPatchEntity::class.java)
        if (patch.id.isBlank()) {
            error("Patch doesn't found by id $id")
        }
        applyPatch(patch)
    }

    fun applyPatch(patch: EcosPatchEntity) {

        val patchId = "${patch.targetApp}$${patch.patchId}"
        log.info { "Apply patch '$patchId'" }

        val result = commandsService.executeSync {
            withTargetApp(patch.targetApp)
            withBody(
                EcosPatchCommandExecutor.Command(
                    patch.type,
                    patch.config,
                    patch.state
                )
            )
            withTtl(Duration.ofSeconds(30))
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
            recordsService.mutate(EntityRef.create(EcosPatchDesc.SRC_ID, patch.id), patch)
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
            recordsService.mutate(EntityRef.create(EcosPatchDesc.SRC_ID, patch.id), patch)
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
                        withSourceId(EcosPatchDesc.SRC_ID)
                        withQuery(
                            Predicates.and(
                                Predicates.eq(
                                    EcosPatchDesc.ATT_STATUS,
                                    EcosPatchStatus.DEPS_WAITING
                                ),
                                Predicates.contains(
                                    EcosPatchDesc.ATT_DEPENDS_ON,
                                    patch.targetApp + "$" + patch.patchId
                                )
                            )
                        )
                    },
                    EcosPatchEntity::class.java
                )
                depsWaitingPatches.getRecords().forEach { depsWaitingPatch ->
                    if (!isAnyPatchNotApplied(depsWaitingPatch.dependsOn)) {
                        depsWaitingPatch.status = EcosPatchStatus.PENDING
                        recordsService.mutate(
                            EntityRef.create(EcosPatchDesc.SRC_ID, depsWaitingPatch.id),
                            depsWaitingPatch
                        )
                    }
                }
            }
        }
    }

    private fun isAppReadyToDeployPatches(appName: String): Boolean {
        val query = RecordsQuery.create {
            withSourceId(EcosPatchDesc.SRC_ID)
            withQuery(
                Predicates.and(
                    Predicates.eq(EcosPatchDesc.ATT_MANUAL, false),
                    Predicates.eq(EcosPatchDesc.ATT_TARGET_APP, appName),
                    Predicates.and(
                        Predicates.eq(EcosPatchDesc.ATT_STATUS, EcosPatchStatus.PENDING),
                        // apply only patches for app with last change more than 10 seconds ago
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
                    Predicates.eq(EcosPatchDesc.ATT_TARGET_APP, appAndId[0]),
                    Predicates.eq(EcosPatchDesc.ATT_PATCH_ID, appAndId[1])
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
                withSourceId(EcosPatchDesc.SRC_ID)
                withQuery(
                    Predicates.and(
                        Predicates.eq(EcosPatchDesc.ATT_STATUS, EcosPatchStatus.APPLIED),
                        Predicates.or(patchIdPredicates)
                    )
                )
            }
        ).getRecords().size < patchIdPredicates.size
    }
}
