package ru.citeck.ecos.apps.domain.artifact.source.service

import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceInfo
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.source.repo.ArtifactSourceMetaEntity
import ru.citeck.ecos.apps.domain.artifact.source.repo.ArtifactSourceMetaRepo
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService
import ru.citeck.ecos.apps.eapps.dto.ArtifactUploadDto
import ru.citeck.ecos.commons.io.file.EcosFile
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
open class EcosArtifactsSourcesService(
    private val artifactSourceMetaRepo: ArtifactSourceMetaRepo,
    private val ecosArtifactTypesService: EcosArtifactTypesService,
    private val ecosArtifactsService: EcosArtifactsService
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val sources: MutableMap<SourceKey, ArtifactsSource> = ConcurrentHashMap()
    private val sinceBySource: MutableMap<SourceKey, Instant> = ConcurrentHashMap()

    private var lastModified = Instant.EPOCH

    init {
        artifactSourceMetaRepo.findAll().forEach {
            sinceBySource[SourceKey(it.appName, it.sourceId, it.sourceType)] = it.lastModified
        }
    }

    fun getLastModified(): Instant {
        return lastModified
    }

    fun addSource(source: ArtifactsSource) {

        val key = SourceKey(source.getAppName(), source.getId(), source.getSourceType())
        sources[key] = source

        log.info { "Add artifacts source: $key" }

        lastModified = Instant.now()
    }

    fun removeSource(source: ArtifactsSource) {

        val key = SourceKey(source.getAppName(), source.getId(), source.getSourceType())

        log.info { "Remove artifacts source: $key" }

        sources.remove(key)
        lastModified = Instant.now()
    }

    fun uploadArtifacts() {
        uploadArtifacts(ecosArtifactTypesService.allTypesDir)
    }

    fun uploadArtifacts(typesDir: EcosFile) {
        uploadArtifacts(sources.values, typesDir)
    }

    private fun uploadArtifacts(sources: Collection<ArtifactsSource>, typesDir: EcosFile) {
        sources.forEach { uploadArtifacts(it, typesDir) }
    }

    @Synchronized
    @Transactional
    protected open fun uploadArtifacts(source: ArtifactsSource, typesDir: EcosFile) {

        val sourceKey = SourceKey(source.getAppName(), source.getId(), source.getSourceType())

        val sourceInfo = ArtifactSourceInfo.create {
            withId(source.getId())
            withType(source.getSourceType())
        }

        val lastModified = source.getLastModified()
        val currentLastModified = sinceBySource.computeIfAbsent(sourceKey) { Instant.EPOCH }

        if (currentLastModified.toEpochMilli() > lastModified.toEpochMilli()) {
            return
        }

        val artifacts: Map<String, List<Any>> = try {
            source.getArtifacts(typesDir, currentLastModified)
        } catch (e: Exception) {
            log.error(e) { "Artifacts can't be received from source: $sourceInfo" }
            emptyMap()
        }

        artifacts.forEach { (type, typeArtifacts) ->

            for (typeArtifact in typeArtifacts) {
                try {
                    ecosArtifactsService.uploadArtifact(ArtifactUploadDto(type, typeArtifact, sourceInfo))
                } catch (e: Exception) {
                    log.error(e) {
                        "Artifact uploading failed. Source: $sourceInfo Type: $type Artifact: $typeArtifact"
                    }
                }
            }
        }

        if (lastModified != currentLastModified) {

            val metaEntity = artifactSourceMetaRepo.findFirstByAppNameAndSourceTypeAndSourceId(
                source.getAppName(),
                source.getSourceType(),
                source.getId()
            )
            val notNullMetaEntity = if (metaEntity != null) {
                metaEntity
            } else {
                val newEntity = ArtifactSourceMetaEntity()
                newEntity.appName = source.getAppName()
                newEntity.sourceId = source.getId()
                newEntity.sourceType = source.getSourceType()
                newEntity
            }
            notNullMetaEntity.lastModified = lastModified
            artifactSourceMetaRepo.save(notNullMetaEntity)
            sinceBySource[sourceKey] = lastModified
        }
    }

    data class SourceKey(
        val appName: String,
        val id: String,
        val type: ArtifactSourceType
    )
}
