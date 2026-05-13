package ru.citeck.ecos.apps.domain.artifact.patch.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import lombok.extern.slf4j.Slf4j
import org.springframework.security.access.annotation.Secured
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.apps.app.common.AppSystemArtifactPerms
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.artifact.ArtifactService
import ru.citeck.ecos.apps.artifact.controller.patch.ArtifactPatch
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.patch.api.records.ArtifactPatchRecordsDao
import ru.citeck.ecos.apps.domain.artifact.patch.dto.ArtifactPatchDto
import ru.citeck.ecos.apps.domain.artifact.patch.repo.ArtifactPatchEntity
import ru.citeck.ecos.apps.domain.artifact.patch.repo.ArtifactPatchRepo
import ru.citeck.ecos.apps.domain.artifact.patch.repo.ArtifactPatchSyncEntity
import ru.citeck.ecos.apps.domain.artifact.patch.repo.ArtifactPatchSyncRepo
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverter
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

@Slf4j
@Service
@Transactional
class EcosArtifactsPatchService(
    private val patchRepo: ArtifactPatchRepo,
    private val patchSyncRepo: ArtifactPatchSyncRepo,
    private val artifactService: ArtifactService,
    private val ecosArtifactsService: EcosArtifactsService,
    private val jpaSearchConverterFactory: JpaSearchConverterFactory,
    private val perms: AppSystemArtifactPerms
) {

    companion object {
        private val log = KotlinLogging.logger {}

        // Legacy: a-w-m patches in third-party repos still ship without the `workspace`
        // field. Auto-fill at save time so the rest of the flow is uniform.
        private const val ADMIN_WORKSPACE_MENU_TYPE = "ui/menu"
        private const val ADMIN_WORKSPACE_MENU_ID = "admin-workspace-menu"
        private const val ADMIN_WORKSPACE = "admin\$workspace"
    }

    private enum class PatchApplyOutcome {
        CHANGED,
        NO_CHANGE,
        ARTIFACT_MISSING,
        PATCH_ERROR
    }

    private val changeListeners: MutableList<Consumer<ArtifactPatchDto?>> = CopyOnWriteArrayList()
    private lateinit var searchConv: JpaSearchConverter<ArtifactPatchEntity>

    init {
        ecosArtifactsService.addArtifactRevUpdateListener { artifactRef ->
            updateArtifactSyncTime(artifactRef.type, artifactRef.id, artifactRef.workspace)
        }
    }

    @PostConstruct
    fun init() {
        searchConv = jpaSearchConverterFactory.createConverter(ArtifactPatchEntity::class.java).build()
    }

    fun getAll(max: Int, skip: Int, predicate: Predicate, sort: List<SortBy>): List<ArtifactPatchDto> {
        val artifacts = searchConv.findAll(patchRepo, predicate, max, skip, sort)
        return artifacts.mapNotNull { toDto(it) }
    }

    fun getCount(predicate: Predicate): Long {
        return searchConv.getCount(patchRepo, predicate)
    }

    fun getCount(): Long {
        return patchRepo.count()
    }

    fun getPatchById(id: String): ArtifactPatchDto? {
        return patchRepo.findFirstByExtId(id)?.let { toDto(it) }
    }

    fun save(patch: ArtifactPatchDto): ArtifactPatchDto? {
        perms.checkWrite(EntityRef.create(AppName.EAPPS, ArtifactPatchRecordsDao.ID, patch.id))

        val patchToSave = ArtifactPatchDto(patch)
        if (patchToSave.workspace.isBlank() && isAdminWorkspaceMenuTarget(patchToSave.target)) {
            patchToSave.workspace = ADMIN_WORKSPACE
        }
        if (patchToSave.sourceType != ArtifactSourceType.USER &&
            !AuthContext.isRunAsSystem() &&
            AuthContext.getCurrentUser().isNotBlank()
        ) {
            patchToSave.sourceType = ArtifactSourceType.USER
        }
        val current = toDto(patchRepo.findFirstByExtId(patchToSave.id))
        if (current != patchToSave) {
            val result = toDto(patchRepo.save(toEntity(patchToSave)))
            changeListeners.forEach(Consumer { it.accept(result) })
            updatePatchSyncTime(patchToSave.target, patchToSave.workspace)
            return result
        }
        return current
    }

    private fun isAdminWorkspaceMenuTarget(target: ArtifactRef?): Boolean {
        target ?: return false
        return target.type == ADMIN_WORKSPACE_MENU_TYPE && target.id == ADMIN_WORKSPACE_MENU_ID
    }

    fun delete(id: String) {
        perms.checkWrite(EntityRef.create(AppName.EAPPS, ArtifactPatchRecordsDao.ID, id))

        val entity = patchRepo.findFirstByExtId(id)
        if (entity != null) {
            patchRepo.delete(entity)
            updatePatchSyncTime(ArtifactRef.valueOf(entity.target), entity.workspace)
        }
    }

    private fun updateArtifactSyncTime(type: String, extId: String, workspace: String) {
        updateSyncEntity(type, extId, workspace) {
            it.artifactLastModified = System.currentTimeMillis()
        }
    }

    private fun updatePatchSyncTime(artifactRef: ArtifactRef, workspace: String) {
        updateSyncEntity(artifactRef.type, artifactRef.id, workspace) {
            it.patchLastModified = System.currentTimeMillis()
        }
    }

    private fun updateSyncEntity(
        type: String,
        extId: String,
        workspace: String,
        action: (ArtifactPatchSyncEntity) -> Unit
    ) {
        val syncEntity = patchSyncRepo.findByArtifact(type, extId, workspace) ?: run {
            val newEntity = ArtifactPatchSyncEntity()
            newEntity.artifactType = type
            newEntity.artifactExtId = extId
            newEntity.workspace = workspace
            newEntity
        }
        action.invoke(syncEntity)
        patchSyncRepo.save(syncEntity)
    }

    @Secured(AuthRole.ADMIN, AuthRole.SYSTEM)
    fun applyOutOfSyncPatches(): Boolean {

        val outOfSync = patchSyncRepo.findOutOfSyncArtifacts()
        if (outOfSync.isEmpty()) {
            return false
        }

        var changed = false

        for (sync in outOfSync) {

            val outcome = if (sync.artifactType.isBlank() || sync.artifactExtId.isBlank()) {
                PatchApplyOutcome.NO_CHANGE
            } else {
                applyPatches(sync.artifactType, sync.artifactExtId, sync.workspace)
            }
            changed = changed || outcome == PatchApplyOutcome.CHANGED

            // Don't mark sync as in-sync on missing-artifact or patching errors. Both can
            // be transient: the artifact may be uploaded later in the same boot (virtual
            // workspace not yet materialized), and patch validation can start passing once
            // the artifact's revision changes. Leave sync out-of-sync so the next job tick
            // retries.
            if (outcome == PatchApplyOutcome.CHANGED || outcome == PatchApplyOutcome.NO_CHANGE) {
                val lastModified = sync.artifactLastModified.coerceAtLeast(sync.patchLastModified)
                sync.artifactLastModified = lastModified
                sync.patchLastModified = lastModified
                patchSyncRepo.save(sync)
            }
        }

        return changed
    }

    private fun applyPatches(type: String, extId: String, workspace: String): PatchApplyOutcome {

        val artifactToPatch = ecosArtifactsService.getArtifactToPatch(type, extId, workspace)
        if (artifactToPatch == null) {
            log.info { "Artifact '$type\$$extId' (workspace='$workspace') can't be patched" }
            return PatchApplyOutcome.ARTIFACT_MISSING
        }

        val patches = getPatchesForArtifact(type, extId, workspace, artifactToPatch.sourceType)
        if (patches.isEmpty()) {
            return if (artifactToPatch.hasPatchedRev) {
                log.info {
                    "Artifact '$type\$$extId' (workspace='$workspace') has patched revision but " +
                        "all patches are gone. Let's remove patched revision"
                }
                ecosArtifactsService.setPatchedRev(type, extId, workspace, null)
                PatchApplyOutcome.CHANGED
            } else {
                PatchApplyOutcome.NO_CHANGE
            }
        }

        return try {
            val patchedArtifact = applyPatches(artifactToPatch.artifact, type, extId, patches)
            if (ecosArtifactsService.setPatchedRev(type, extId, workspace, patchedArtifact)) {
                PatchApplyOutcome.CHANGED
            } else {
                PatchApplyOutcome.NO_CHANGE
            }
        } catch (e: Exception) {
            log.error(e) {
                "Patching error. Artifact: '$type\$$extId' (workspace='$workspace') Patches: $patches"
            }
            PatchApplyOutcome.PATCH_ERROR
        }
    }

    private fun applyPatches(
        artifact: Any,
        type: String,
        extId: String,
        patches: List<ArtifactPatchDto>
    ): Any {

        val artifactPatches = mapper.convert<List<ArtifactPatch>>(
            patches,
            mapper.getListType(ArtifactPatch::class.java)
        )
        if (artifactPatches.isNullOrEmpty()) {
            return artifact
        }
        log.info { "Apply " + artifactPatches.size + " patches to '$type\$$extId'" }
        return artifactService.applyPatches(type, artifact, artifactPatches)
    }

    private fun getPatchesForArtifact(
        type: String,
        extId: String,
        workspace: String,
        sourceType: ArtifactSourceType
    ): List<ArtifactPatchDto> {
        val allowedPatchSourceTypes: List<ArtifactSourceType> = when (sourceType) {
            ArtifactSourceType.APPLICATION -> emptyList() // any source
            ArtifactSourceType.USER -> return emptyList() // user artifacts can't be patched
            ArtifactSourceType.ECOS_APP -> listOf(
                ArtifactSourceType.APPLICATION,
                ArtifactSourceType.ECOS_APP,
                ArtifactSourceType.USER
            )
        }
        val target = ArtifactRef.create(type, extId).toString()
        val patchEntities = if (allowedPatchSourceTypes.isNotEmpty()) {
            patchRepo.findAllByEnabledTrueAndTargetAndWorkspaceAndSourceTypeIn(
                target,
                workspace,
                allowedPatchSourceTypes
            )
        } else {
            patchRepo.findAllByEnabledTrueAndTargetAndWorkspace(target, workspace)
        }
        return patchEntities.mapNotNull { toDto(it) }
            .sortedBy { it.id }
            .sortedBy { it.order }
    }

    fun addListener(listener: Consumer<ArtifactPatchDto?>) {
        changeListeners.add(listener)
    }

    private fun toDto(entity: ArtifactPatchEntity?): ArtifactPatchDto? {
        if (entity == null) {
            return null
        }
        var name = mapper.read(entity.name, MLText::class.java)
        if (name == null) {
            name = MLText("")
        }
        var config = mapper.read(entity.config, ObjectData::class.java)
        if (config == null) {
            config = ObjectData.create()
        }
        val result = ArtifactPatchDto()
        result.id = entity.extId
        result.config = config
        result.name = name
        result.order = entity.order
        result.target = ArtifactRef.valueOf(entity.target)
        result.workspace = entity.workspace
        result.type = entity.type
        result.sourceType = entity.sourceType
        result.enabled = entity.enabled
        return result
    }

    private fun toEntity(patch: ArtifactPatchDto): ArtifactPatchEntity {
        var entity = patchRepo.findFirstByExtId(patch.id)
        if (entity == null) {
            entity = ArtifactPatchEntity()
            entity.extId = patch.id
        }
        entity.config = mapper.toString(patch.config) ?: "{}"
        entity.name = mapper.toString(patch.name)
        entity.order = patch.order
        entity.target = patch.target.toString()
        entity.workspace = patch.workspace
        entity.type = patch.type
        entity.sourceType = patch.sourceType
        entity.enabled = patch.enabled
        return entity
    }
}
