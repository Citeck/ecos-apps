package ru.citeck.ecos.apps.domain.artifact.patch.service

import lombok.extern.slf4j.Slf4j
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.artifact.ArtifactService
import ru.citeck.ecos.apps.artifact.controller.patch.ArtifactPatch
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.patch.dto.ArtifactPatchDto
import ru.citeck.ecos.apps.domain.artifact.patch.repo.ArtifactPatchEntity
import ru.citeck.ecos.apps.domain.artifact.patch.repo.ArtifactPatchRepo
import ru.citeck.ecos.apps.domain.artifact.patch.repo.ArtifactPatchSyncEntity
import ru.citeck.ecos.apps.domain.artifact.patch.repo.ArtifactPatchSyncRepo
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverter
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import javax.annotation.PostConstruct

@Slf4j
@Service
@Transactional
class EcosArtifactsPatchService(
    private val patchRepo: ArtifactPatchRepo,
    private val patchSyncRepo: ArtifactPatchSyncRepo,
    private val artifactService: ArtifactService,
    private val ecosArtifactsService: EcosArtifactsService,
    private val jpaSearchConverterFactory: JpaSearchConverterFactory
) {

    companion object {
        private val log = KotlinLogging.logger {}
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

    fun getCount(predicate: Predicate): Int {
        return searchConv.getCount(patchRepo, predicate).toInt()
    }

    fun getCount(): Int {
        return patchRepo.count().toInt()
    }

    fun getPatchById(id: String): ArtifactPatchDto? {
        return patchRepo.findFirstByExtId(id)?.let { toDto(it) }
    }

    fun save(patch: ArtifactPatchDto): ArtifactPatchDto? {
        val current = toDto(patchRepo.findFirstByExtId(patch.id))
        if (current != patch) {
            val result = toDto(patchRepo.save(toEntity(patch)))
            changeListeners.forEach(Consumer { it.accept(result) })
            updatePatchSyncTime(patch.target)
            return result
        }
        return current
    }

    fun delete(id: String) {
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

    fun applyOutOfSyncPatches(): Boolean {

        val outOfSync = patchSyncRepo.findOutOfSyncArtifacts()
        if (outOfSync.isEmpty()) {
            return false
        }

        var changed = false

        for (sync in outOfSync) {

            val artifactRef = ArtifactRef.create(sync.artifactType, sync.artifactExtId)
            changed = applyPatches(artifactRef) || changed
            val lastModified = sync.artifactLastModified.coerceAtLeast(sync.patchLastModified)

            sync.artifactLastModified = lastModified
            sync.patchLastModified = lastModified

            patchSyncRepo.save(sync)
        }

        return changed
    }

    private fun applyPatches(artifactRef: ArtifactRef): Boolean {

        val artifactToPatch = ecosArtifactsService.getArtifactToPatch(artifactRef) ?: return false

        val patches = getPatchesForArtifact(artifactRef)
        if (patches.isEmpty()) {
            return false
        }

        var wasChanged = false
        try {
            val patchedArtifact = applyPatches(artifactToPatch, artifactRef, patches)
            wasChanged = ecosArtifactsService.setPatchedRev(artifactRef, patchedArtifact) || wasChanged
        } catch (e: Exception) {
            log.error { "Patching error. Artifact: $artifactRef Patches: $patches" }
        }

        return wasChanged
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
        if (artifactPatches == null || artifactPatches.isEmpty()) {
            return artifact
        }
        log.info("Apply " + artifactPatches.size + " patches to " + artifactRef)
        return artifactService.applyPatches(artifactRef.type, artifact, artifactPatches)
    }

    private fun getPatchesForArtifact(artifactRef: ArtifactRef): List<ArtifactPatchDto> {
        val patchEntities = patchRepo.findAllByTarget(artifactRef.toString())
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
        return entity
    }
}
