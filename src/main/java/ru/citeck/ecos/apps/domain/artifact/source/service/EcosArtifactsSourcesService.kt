package ru.citeck.ecos.apps.domain.artifact.source.service

import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.apps.app.domain.artifact.source.AppSourceKey
import ru.citeck.ecos.apps.app.domain.artifact.source.SourceKey
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

    private val sources: MutableMap<AppSourceKey, AppArtifactsSource> = ConcurrentHashMap()
    private val sinceBySource: MutableMap<AppSourceKey, Instant> = ConcurrentHashMap()

    private var lastModified = Instant.EPOCH

    init {
        artifactSourceMetaRepo.findAll().forEach {
            sinceBySource[AppSourceKey(it.appName, SourceKey(it.sourceId, it.sourceType))] = it.lastModified
        }
    }

    fun getLastModified(): Instant {
        return lastModified
    }

    fun addSource(source: AppArtifactsSource) {

        sources[source.getKey()] = source

        log.info { "Add artifacts source: ${source.getKey()}" }

        lastModified = Instant.now()
    }

    fun removeSource(source: AppArtifactsSource) {

        log.info { "Remove artifacts source: ${source.getKey()}" }

        sources.remove(source.getKey())
        lastModified = Instant.now()
    }

    fun uploadArtifacts() {
        uploadArtifacts(ecosArtifactTypesService.allTypesDir)
    }

    fun uploadArtifacts(typesDir: EcosFile) {
        uploadArtifacts(sources.values, typesDir)
    }

    private fun uploadArtifacts(sources: Collection<AppArtifactsSource>, typesDir: EcosFile) {
        sources.forEach { uploadArtifacts(it, typesDir) }
    }

    @Synchronized
    @Transactional
    protected open fun uploadArtifacts(source: AppArtifactsSource, typesDir: EcosFile) {

        val appSourceKey = source.getKey()

        val lastModified = source.getLastModified()
        val currentLastModified = sinceBySource.computeIfAbsent(appSourceKey) { Instant.EPOCH }

        if (currentLastModified.toEpochMilli() > lastModified.toEpochMilli()) {
            return
        }

        val artifacts: Map<String, List<Any>> = try {
            source.getArtifacts(typesDir, currentLastModified)
        } catch (e: Exception) {
            log.error(e) { "Artifacts can't be received from source: $appSourceKey" }
            emptyMap()
        }

        artifacts.forEach { (type, typeArtifacts) ->

            for (typeArtifact in typeArtifacts) {
                try {
                    ecosArtifactsService.uploadArtifact(ArtifactUploadDto(type, typeArtifact, appSourceKey))
                } catch (e: Exception) {
                    log.error(e) {
                        "Artifact uploading failed. Source: $appSourceKey Type: $type Artifact: $typeArtifact"
                    }
                }
            }
        }

        if (lastModified != currentLastModified) {

            val metaEntity = artifactSourceMetaRepo.findFirstByAppNameAndSourceTypeAndSourceId(
                appSourceKey.appName,
                appSourceKey.source.type,
                appSourceKey.source.id
            )
            val notNullMetaEntity = if (metaEntity != null) {
                metaEntity
            } else {
                val newEntity = ArtifactSourceMetaEntity()
                newEntity.appName = appSourceKey.appName
                newEntity.sourceType = appSourceKey.source.type
                newEntity.sourceId = appSourceKey.source.id
                newEntity
            }
            notNullMetaEntity.lastModified = lastModified
            artifactSourceMetaRepo.save(notNullMetaEntity)
            sinceBySource[appSourceKey] = lastModified
        }
    }
}
