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
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
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
    private val workspaceService: WorkspaceService,
    private val perms: AppSystemArtifactPerms
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val ADMIN_WORKSPACE_MENU_TYPE = "ui/menu"
        private const val ADMIN_WORKSPACE_MENU_ID = "admin-workspace-menu"
        private const val ADMIN_WORKSPACE = "admin\$workspace"
    }

    private val changeListeners: MutableList<Consumer<ArtifactPatchDto?>> = CopyOnWriteArrayList()
    private lateinit var searchConv: JpaSearchConverter<ArtifactPatchEntity>

    init {
        ecosArtifactsService.addArtifactRevUpdateListener {
            updateArtifactSyncTime(it)
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
            updatePatchSyncTime(patchToSave.target)
            return result
        }
        return current
    }

    fun delete(id: String) {
        perms.checkWrite(EntityRef.create(AppName.EAPPS, ArtifactPatchRecordsDao.ID, id))

        val entity = patchRepo.findFirstByExtId(id)
        if (entity != null) {
            patchRepo.delete(entity)
            updatePatchSyncTime(ArtifactRef.valueOf(entity.target))
        }
    }

    private fun updateArtifactSyncTime(artifactRef: ArtifactRef) {
        updateSyncEntity(artifactRef) {
            it.artifactLastModified = System.currentTimeMillis()
        }
    }

    private fun updatePatchSyncTime(artifactRef: ArtifactRef) {
        updateSyncEntity(artifactRef) {
            it.patchLastModified = System.currentTimeMillis()
        }
    }

    private fun updateSyncEntity(artifactRef: ArtifactRef, action: (ArtifactPatchSyncEntity) -> Unit) {
        val syncEntity = patchSyncRepo.findByArtifact(artifactRef.type, artifactRef.id) ?: run {
            val newEntity = ArtifactPatchSyncEntity()
            newEntity.artifactType = artifactRef.type
            newEntity.artifactExtId = artifactRef.id
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

            val artifactRef = if (sync.artifactType.isBlank() || sync.artifactExtId.isBlank()) {
                ArtifactRef.EMPTY
            } else {
                ArtifactRef.create(sync.artifactType, sync.artifactExtId)
            }
            changed = applyPatches(artifactRef) || changed
            val lastModified = sync.artifactLastModified.coerceAtLeast(sync.patchLastModified)

            sync.artifactLastModified = lastModified
            sync.patchLastModified = lastModified

            patchSyncRepo.save(sync)
        }

        return changed
    }

    private fun applyPatches(artifactRef: ArtifactRef): Boolean {

        if (artifactRef === ArtifactRef.EMPTY) {
            return false
        }

        val resolvedRef = resolveArtifactRefForLookup(artifactRef)

        val artifactToPatch = ecosArtifactsService.getArtifactToPatch(resolvedRef)
        if (artifactToPatch == null) {
            log.info { "Artifact '$resolvedRef' can't be patched" }
            return false
        }

        // Use the original (workspace-less) ref for patch lookup so existing patches
        // targeting `ui/menu$admin-workspace-menu` still match.
        val patches = getPatchesForArtifact(artifactRef, artifactToPatch.sourceType)
        if (patches.isEmpty()) {
            return if (artifactToPatch.hasPatchedRev) {
                log.info {
                    "Artifact '$resolvedRef' has patched revision but " +
                        "all patches are gone. Let's remove patched revision"
                }
                ecosArtifactsService.setPatchedRev(resolvedRef, null)
                true
            } else {
                false
            }
        }

        var wasChanged = false
        try {
            val patchedArtifact = applyPatches(artifactToPatch.artifact, resolvedRef, patches)
            wasChanged = ecosArtifactsService.setPatchedRev(resolvedRef, patchedArtifact)
        } catch (e: Exception) {
            log.error { "Patching error. Artifact: $resolvedRef Patches: $patches" }
        }

        return wasChanged
    }

    /**
     * Backwards-compat kludge for the admin-workspace menu. Existing patches in DB and on
     * classpath target `ui/menu$admin-workspace-menu` with no workspace prefix, but after
     * the menu artifact got `workspace="admin$workspace"` it lives at
     * `ui/menu$<wsSysId>:admin-workspace-menu`. Rewrite the workspace-less ref so the
     * artifact lookup hits the right row; patch lookup keeps the original target string.
     */
    private fun resolveArtifactRefForLookup(ref: ArtifactRef): ArtifactRef {
        if (ref.wsSysId.isEmpty() &&
            ref.type == ADMIN_WORKSPACE_MENU_TYPE &&
            ref.id == ADMIN_WORKSPACE_MENU_ID
        ) {
            val wsSysId = workspaceService.getWorkspaceSystemId(ADMIN_WORKSPACE)
            if (wsSysId.isNotEmpty()) {
                return ArtifactRef.create(ref.type, ref.id, wsSysId)
            }
        }
        return ref
    }

    private fun applyPatches(
        artifact: Any,
        artifactRef: ArtifactRef,
        patches: List<ArtifactPatchDto>
    ): Any {

        val artifactPatches = mapper.convert<List<ArtifactPatch>>(
            patches,
            mapper.getListType(ArtifactPatch::class.java)
        )
        if (artifactPatches.isNullOrEmpty()) {
            return artifact
        }
        log.info { "Apply " + artifactPatches.size + " patches to " + artifactRef }
        return artifactService.applyPatches(artifactRef.type, artifact, artifactPatches)
    }

    private fun getPatchesForArtifact(artifactRef: ArtifactRef, sourceType: ArtifactSourceType): List<ArtifactPatchDto> {
        val allowedPatchSourceTypes: List<ArtifactSourceType> = when (sourceType) {
            ArtifactSourceType.APPLICATION -> emptyList() // any source
            ArtifactSourceType.USER -> return emptyList() // user artifacts can't be patched
            ArtifactSourceType.ECOS_APP -> listOf(
                ArtifactSourceType.APPLICATION,
                ArtifactSourceType.ECOS_APP,
                ArtifactSourceType.USER
            )
        }
        val patchEntities = if (allowedPatchSourceTypes.isNotEmpty()) {
            patchRepo.findAllByEnabledTrueAndTargetAndSourceTypeIn(artifactRef.toString(), allowedPatchSourceTypes)
        } else {
            patchRepo.findAllByEnabledTrueAndTarget(artifactRef.toString())
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
        entity.type = patch.type
        entity.sourceType = patch.sourceType
        entity.enabled = patch.enabled
        return entity
    }
}
