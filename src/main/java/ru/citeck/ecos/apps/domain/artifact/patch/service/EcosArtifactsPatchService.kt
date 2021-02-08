package ru.citeck.ecos.apps.domain.artifact.patch.service

import lombok.extern.slf4j.Slf4j
import mu.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
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
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Root

@Slf4j
@Service
@Transactional
class EcosArtifactsPatchService(
    private val patchRepo: ArtifactPatchRepo,
    private val patchSyncRepo: ArtifactPatchSyncRepo,
    private val artifactService: ArtifactService,
    private val ecosArtifactsService: EcosArtifactsService
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val changeListeners: MutableList<Consumer<ArtifactPatchDto?>> = CopyOnWriteArrayList()

    init {
        ecosArtifactsService.addArtifactRevUpdateListener {
            updateArtifactSyncTime(it)
        }
    }

    fun getAll(max: Int, skip: Int, predicate: Predicate?): List<ArtifactPatchDto> {
        val page = PageRequest.of(skip / max, max, Sort.by(Sort.Direction.DESC, "id"))
        val artifacts: Page<ArtifactPatchEntity>
        artifacts = if (predicate == null) {
            patchRepo.findAll(page)
        } else {
            patchRepo.findAll(toSpec(predicate), page)
        }
        return artifacts.mapNotNull { toDto(it) }
    }

    fun getCount(predicate: Predicate?): Int {
        if (predicate == null) {
            return getCount()
        }
        val spec = toSpec(predicate)
        return if (spec != null) {
            patchRepo.count(spec).toInt()
        } else {
            getCount()
        }
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
        updateSyncEntity(artifactRef) { it.artifactLastModified = System.nanoTime() }
    }

    private fun updatePatchSyncTime(artifactRef: ArtifactRef) {
        updateSyncEntity(artifactRef) { it.patchLastModified = System.nanoTime() }
    }

    private fun updateSyncEntity(artifactRef: ArtifactRef, action: (ArtifactPatchSyncEntity) -> Unit) {
        val syncEntity = patchSyncRepo.findByArtifact(artifactRef.type, artifactRef.id) ?: {
            val newEntity = ArtifactPatchSyncEntity()
            newEntity.artifactType = artifactRef.type
            newEntity.artifactExtId = artifactRef.id
            newEntity
        }()
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

    private fun applyPatches(artifact: Any,
                     artifactRef: ArtifactRef,
                     patches: List<ArtifactPatchDto>): Any {

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
        return patchEntities.mapNotNull { toDto(it) }.sortedBy { it.order }
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

    private fun toSpec(predicate: Predicate): Specification<ArtifactPatchEntity>? {
        val predicateDto = PredicateUtils.convertToDto(predicate, PredicateDto::class.java)
        var spec: Specification<ArtifactPatchEntity>? = null

        val name = predicateDto.name
        if (!name.isNullOrBlank()) {
            spec = Specification {
                root: Root<ArtifactPatchEntity>, _: CriteriaQuery<*>?, builder: CriteriaBuilder ->
                    builder.like(
                        builder.lower(root.get("name")), "%" + name.toLowerCase() + "%"
                    )
            }
        }
        val artifactId = predicateDto.artifactId ?: predicateDto.moduleId
        if (!artifactId.isNullOrBlank()) {
            val idSpec = Specification {
                root: Root<ArtifactPatchEntity>, _: CriteriaQuery<*>?, builder: CriteriaBuilder ->
                    builder.like(
                        builder.lower(root.get("extId")), "%" + artifactId.toLowerCase() + "%"
                    )
            }
            spec = spec?.or(idSpec) ?: idSpec
        }
        return spec
    }

    class PredicateDto(
        val name: String? = null,
        val moduleId: String? = null,
        val artifactId: String? = null
    )
}
