package ru.citeck.ecos.apps.domain.patch.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import ru.citeck.ecos.apps.domain.artifact.application.job.ApplicationsWatcherJob
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.patch.desc.EcosPatchDesc
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.task.schedule.Schedules
import ru.citeck.ecos.context.lib.auth.AuthContext
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
import ru.citeck.ecos.webapp.lib.patch.PatchTypeMetaRegistry
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
    val ecosAppLockService: EcosAppLockService,
    val patchTypeMetaRegistry: PatchTypeMetaRegistry,
    val refsDeployChecker: PatchRefsDeployChecker,
    val ecosArtifactsService: EcosArtifactsService,
    val patchDeploySyncService: PatchDeploySyncService
) {

    companion object {

        private const val SCHEDULER_ID = "ecos-patches"
        private val ECOS_PATCHES_LOCK_KEY = EcosPatchService::class.jvmName + "-$SCHEDULER_ID-lock"

        private val log = KotlinLogging.logger {}

        // Patch types that were registered before the patch-type-meta registry existed. They may
        // target old (parent pre-3.27) apps that never publish to PatchTypeMetaRegistry, so they must not
        // wait for the target app to register.
        private val PRE_REGISTRY_PATCH_TYPES = setOf("mutate", "delete", "bean")

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

    /**
     * Forces the next job tick to reconcile DEPS_WAITING patches even when the deploy watermark
     * looks in-sync. Set on the first tick (recovers from a crash that left a patch in DEPS_WAITING
     * after its deps were already deployed) and whenever the patch-type-meta registry updates —
     * a refs-patch parked before a restart can only be resolved once its targetApp republishes its
     * meta to [PatchTypeMetaRegistry], which may happen after that first tick.
     */
    @Volatile
    private var forceDepsReconcile = true

    @PostConstruct
    fun init() {
        patchTypeMetaRegistry.onDataUpdated {
            AuthContext.runAsSystem {
                wakeTargetWaitingPatches()
                // Registry (re)populated: a DEPS_WAITING refs-patch whose meta was empty on the
                // first tick can now be resolved — arm a reconcile for the next job tick.
                forceDepsReconcile = true
            }
        }
        // Stamp the deploy watermark inside the deploy transaction. The patch job (below) reads it
        // and reconciles DEPS_WAITING patches — no in-memory signal, so it survives restarts and
        // works even when the deploy runs on a different node than the patch job.
        ecosArtifactsService.addArtifactDeployedListener {
            patchDeploySyncService.markArtifactsDeployed()
        }
        ecosWebAppApi.doWhenAppReady {
            ecosTasksApi.getScheduler(SCHEDULER_ID).schedule(
                "Citeck patch task",
                Schedules.fixedDelay(properties.job.delayDuration)
            ) {
                ecosAppLockService.doInSyncOrSkip(ECOS_PATCHES_LOCK_KEY) { lockCtx ->
                    reconcileDepsWaitingPatches()
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

        if (!isAppPatchesSettled(appName)) {
            log.trace { "Patches for app '$appName' are still being collected; waiting for a quiet period" }
            return false
        }

        val query = RecordsQuery.create {
            withSourceId(EcosPatchDesc.SRC_ID)
            withQuery(
                Predicates.and(
                    Predicates.eq(EcosPatchDesc.ATT_TARGET_APP, appName),
                    Predicates.or(
                        Predicates.empty(EcosPatchDesc.ATT_DEPENDS_ON_APPS),
                        ValuePredicate.contains(EcosPatchDesc.ATT_DEPENDS_ON_APPS, availableApps),
                    ),
                    // `manual` gates only the initial PENDING launch — a manual patch is started by
                    // an explicit ApplyEcosPatchAction. Once started it lands in IN_PROGRESS (batched,
                    // must auto-continue across batches) or FAILED (retried per errorDelayDistribution),
                    // and from there the scheduler drives it like any other patch, without re-triggering.
                    Predicates.or(
                        Predicates.and(
                            Predicates.eq(EcosPatchDesc.ATT_MANUAL, false),
                            Predicates.eq(EcosPatchDesc.ATT_STATUS, EcosPatchStatus.PENDING)
                        ),
                        Predicates.and(
                            Predicates.or(
                                Predicates.eq(EcosPatchDesc.ATT_STATUS, EcosPatchStatus.FAILED),
                                Predicates.eq(EcosPatchDesc.ATT_STATUS, EcosPatchStatus.IN_PROGRESS)
                            ),
                            Predicates.notEmpty(EcosPatchDesc.ATT_NEXT_EXEC_DATE),
                            Predicates.lt(EcosPatchDesc.ATT_NEXT_EXEC_DATE, Instant.now())
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

    /**
     * Entry point of the explicit "apply patch" action. Unlike the scheduler, it may be called for a
     * patch in any status, including an already applied one.
     */
    fun applyPatch(id: String) {
        val patch = recordsService.getAtts(EcosPatchDesc.getRef(id), EcosPatchEntity::class.java)
        if (patch.id.isBlank()) {
            error("Patch doesn't found by id $id")
        }
        // Restarting a finished batched patch means running it from the first batch again: its
        // stored offset is at the end of the batch field, so without this reset there would be
        // nothing left to process and the restart would silently do nothing. The offset is our own
        // orchestration state, so it is dropped together with the executor state, exactly like a
        // redeploy with a newer date does (see EcosPatchConfig).
        if (patch.status == EcosPatchStatus.APPLIED && PatchBatchUtils.isBatched(patch.batch)) {
            patch.state = ObjectData.create()
        }
        applyPatch(patch)
    }

    fun applyPatch(patch: EcosPatchEntity) {

        // New-registry types wait for the target app to publish its patch metadata; pre-registry
        // types may run on apps that don't publish, so they skip only this registration wait.
        if (patch.type !in PRE_REGISTRY_PATCH_TYPES && !patchTypeMetaRegistry.isAppRegistered(patch.targetApp)) {
            setWaitingStatus(
                patch,
                EcosPatchStatus.TARGET_WAITING,
                "target app '${patch.targetApp}' is not registered in PatchTypeMetaRegistry yet"
            )
            return
        }

        // Dependency check applies to every type: the metadata is read from the registry (empty for
        // apps that haven't published), so pre-registry types are also honoured if they declare it.
        val dependsOnRefsPaths = patchTypeMetaRegistry.getMeta(patch.targetApp, patch.type).dependsOnRefs
        if (dependsOnRefsPaths.isNotEmpty()) {
            val refs = PatchRefsDeployChecker.resolveRefs(patch.config, dependsOnRefsPaths)
            if (!refsDeployChecker.allDeployed(refs)) {
                setWaitingStatus(patch, EcosPatchStatus.DEPS_WAITING, "waits for artifacts: $refs")
                return
            }
        }

        val patchId = "${patch.targetApp}$${patch.patchId}"
        log.info { "Apply patch '$patchId'" }

        val batched = PatchBatchUtils.isBatched(patch.batch)
        val slice = if (batched) {
            PatchBatchUtils.buildBatch(patch.config, patch.batch, patch.state[EcosPatchDesc.STATE_BATCH_OFFSET].asInt(0))
        } else {
            null
        }
        if (slice != null && slice.isEmpty) {
            // Nothing left to process: the batch field is empty, or the offset is already at its end
            // (an applied patch executed again without a state reset). Complete the patch locally —
            // sending an empty slice to the target app would fail it, because executors such as
            // 'mutate' and 'delete' reject an empty records list.
            if (!patch.config[patch.batch.field].isArray()) {
                // Nothing was and will be processed: most likely 'batch.field' doesn't match the
                // config. Log it, otherwise such a patch would silently end up applied.
                log.warn {
                    "Patch '$patchId' is batched by field '${patch.batch.field}', " +
                        "but its config doesn't contain an array in this field"
                }
            }
            completePatchWithoutExecution(patch, slice.newOffset, patchId)
            return
        }

        val commandConfig = slice?.config ?: patch.config
        // A batched executor sees only its own state (nested under STATE_COMMAND_STATE), not the
        // orchestration state; a non-batched one owns the whole state object as before.
        val commandState = if (batched) {
            val stored = patch.state[EcosPatchDesc.STATE_COMMAND_STATE]
            if (stored.isObject()) ObjectData.create(stored) else ObjectData.create()
        } else {
            patch.state
        }

        val result = commandsService.executeSync {
            withTargetApp(patch.targetApp)
            withBody(
                EcosPatchCommandExecutor.Command(
                    patch.type,
                    commandConfig,
                    commandState
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
            patch.errorsCount = 0
            patch.lastError = null
            if (batched && slice != null) {
                patch.state[EcosPatchDesc.STATE_BATCH_OFFSET] = slice.newOffset
                patch.state[EcosPatchDesc.STATE_COMMAND_STATE] = commRes?.result?.state ?: ObjectData.create()
                patch.status = if (slice.completed) {
                    patch.nextExecDate = null
                    EcosPatchStatus.APPLIED
                } else {
                    patch.nextExecDate = Instant.now()
                    EcosPatchStatus.IN_PROGRESS
                }
            } else {
                patch.state = commRes?.result?.state ?: ObjectData.create()
                patch.status = if (commRes?.result?.completed == true) {
                    patch.nextExecDate = null
                    EcosPatchStatus.APPLIED
                } else {
                    patch.nextExecDate = commRes?.result?.nextExecutionTime
                    EcosPatchStatus.IN_PROGRESS
                }
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
                wakeDependentPatches(patch)
            }
        }
    }

    /**
     * Marks a batched patch as applied without calling the target app, for a batch that has nothing
     * left to process. Keeps the executor state (STATE_COMMAND_STATE) as is — no execution happened,
     * so there is no new state to store.
     */
    private fun completePatchWithoutExecution(patch: EcosPatchEntity, offset: Int, patchId: String) {
        patch.errorsCount = 0
        patch.lastError = null
        patch.state[EcosPatchDesc.STATE_BATCH_OFFSET] = offset
        patch.nextExecDate = null
        patch.status = EcosPatchStatus.APPLIED
        recordsService.mutate(EntityRef.create(EcosPatchDesc.SRC_ID, patch.id), patch)
        log.info { "Patch '$patchId' has no batch items to process and was marked as applied" }
        wakeDependentPatches(patch)
    }

    /**
     * Moves patches waiting for the just applied one back to PENDING, when all their dependencies
     * are applied.
     */
    private fun wakeDependentPatches(patch: EcosPatchEntity) {
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

    /**
     * Parks the patch in a reactive waiting status (no nextExecDate/polling). The patch is woken
     * up explicitly: TARGET_WAITING by [wakeTargetWaitingPatches] on registry updates, DEPS_WAITING
     * (dependsOnRefs) by [reconcileDepsWaitingPatches] once the deploy watermark advances.
     */
    private fun setWaitingStatus(patch: EcosPatchEntity, status: EcosPatchStatus, reason: String) {
        patch.status = status
        recordsService.mutate(EntityRef.create(EcosPatchDesc.SRC_ID, patch.id), patch)
        log.info { "Patch '${patch.targetApp}\$${patch.patchId}' set to $status: $reason" }
    }

    private fun wakeTargetWaitingPatches() {
        try {
            val targetWaitingPatches = recordsService.query(
                RecordsQuery.create {
                    withSourceId(EcosPatchDesc.SRC_ID)
                    withQuery(
                        Predicates.eq(EcosPatchDesc.ATT_STATUS, EcosPatchStatus.TARGET_WAITING)
                    )
                },
                EcosPatchEntity::class.java
            )
            targetWaitingPatches.getRecords().forEach { patch ->
                if (patchTypeMetaRegistry.isAppRegistered(patch.targetApp)) {
                    patch.status = EcosPatchStatus.PENDING
                    recordsService.mutate(EntityRef.create(EcosPatchDesc.SRC_ID, patch.id), patch)
                }
            }
        } catch (e: Throwable) {
            log.error(e) { "Error while waking TARGET_WAITING patches" }
        }
    }

    /**
     * Wakes DEPS_WAITING patches whose `dependsOnRefs` artifacts are now all deployed. Driven by the
     * persisted deploy watermark ([PatchDeploySyncService]) rather than per-deploy callbacks: the
     * scan runs only when the watermark shows a deploy the job hasn't reacted to yet (or on the
     * first tick, for crash recovery), so an idle system does no work. The observed deploy date is
     * captured before the scan and committed back via a conditional update — if a deploy lands
     * mid-scan, the mark is rejected and the next tick reconciles again, so no wake is lost.
     *
     * Patches parked in DEPS_WAITING because of an unmet `dependsOn` (patch-dependency, empty
     * dependsOnRefs) are left alone here — those are reactivated by the APPLIED-path logic in
     * [applyPatch].
     */
    private fun reconcileDepsWaitingPatches() {
        try {
            // Reset the force flag up front (capture-and-clear): a registry update landing during
            // the scan re-arms it, so the next tick reconciles again instead of losing the signal.
            val forced = forceDepsReconcile
            forceDepsReconcile = false

            val state = patchDeploySyncService.getState()
            val settled = System.currentTimeMillis() - state.deployDate >=
                properties.deploySettleDuration.toMillis()
            if (!forced && !(state.isOutOfSync() && settled)) {
                return
            }
            // Capture the deploy date BEFORE the scan so markSynced can detect a deploy that lands
            // while we're still reconciling.
            val observedDeployDate = state.deployDate

            val depsWaitingPatches = recordsService.query(
                RecordsQuery.create {
                    withSourceId(EcosPatchDesc.SRC_ID)
                    withQuery(
                        Predicates.eq(EcosPatchDesc.ATT_STATUS, EcosPatchStatus.DEPS_WAITING)
                    )
                },
                EcosPatchEntity::class.java
            )
            depsWaitingPatches.getRecords().forEach { patch ->
                val paths = patchTypeMetaRegistry.getMeta(patch.targetApp, patch.type).dependsOnRefs
                if (paths.isEmpty()) {
                    return@forEach
                }
                val refs = PatchRefsDeployChecker.resolveRefs(patch.config, paths)
                if (refsDeployChecker.allDeployed(refs)) {
                    patch.status = EcosPatchStatus.PENDING
                    recordsService.mutate(EntityRef.create(EcosPatchDesc.SRC_ID, patch.id), patch)
                }
            }

            patchDeploySyncService.markSynced(observedDeployDate)
        } catch (e: Throwable) {
            log.error(e) { "Error while reconciling DEPS_WAITING patches" }
        }
    }

    /**
     * True when the app's pending patches have "settled" — none was modified within the last
     * [EcosPatchProperties.appReadyThresholdDuration]. Patches for an app arrive from several sources
     * over a short window, so we wait for a quiet period before applying any, to be sure the whole
     * set (and its dependsOn ordering) is already collected.
     */
    private fun isAppPatchesSettled(appName: String): Boolean {
        val query = RecordsQuery.create {
            withSourceId(EcosPatchDesc.SRC_ID)
            withQuery(
                Predicates.and(
                    Predicates.eq(EcosPatchDesc.ATT_MANUAL, false),
                    Predicates.eq(EcosPatchDesc.ATT_TARGET_APP, appName),
                    Predicates.and(
                        Predicates.eq(EcosPatchDesc.ATT_STATUS, EcosPatchStatus.PENDING),
                        // a PENDING patch modified within the threshold means the set is still
                        // being collected from all sources — not settled yet
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
