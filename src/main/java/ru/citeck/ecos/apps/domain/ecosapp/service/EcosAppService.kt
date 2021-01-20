package ru.citeck.ecos.apps.domain.ecosapp.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceInfo
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType
import ru.citeck.ecos.apps.app.domain.artifact.source.SourceKey
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.artifact.ArtifactService
import ru.citeck.ecos.apps.artifact.type.TypeContext
import ru.citeck.ecos.apps.domain.artifact.application.job.ApplicationsWatcherJob
import ru.citeck.ecos.apps.domain.artifact.artifact.api.records.EcosArtifactRecords
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity
import ru.citeck.ecos.apps.domain.content.service.EcosContentDao
import ru.citeck.ecos.apps.domain.ecosapp.dto.EcosAppDef
import ru.citeck.ecos.apps.domain.ecosapp.repo.EcosAppEntity
import ru.citeck.ecos.apps.domain.ecosapp.repo.EcosAppRepo
import ru.citeck.ecos.apps.spring.app.AdditionalSourceProvider
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.Version
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.commons.utils.NameUtils
import ru.citeck.ecos.commons.utils.ZipUtils
import ru.citeck.ecos.records2.RecordRef
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.*

@Service
@Transactional
class EcosAppService(
    private val ecosAppRepo: EcosAppRepo,
    private val ecosArtifactsService: EcosArtifactsService,
    private val ecosContentDao: EcosContentDao,
    private val artifactService: ArtifactService,
    private val applicationsWatcherJob: ApplicationsWatcherJob
) : AdditionalSourceProvider {

    fun uploadZip(data: ByteArray) : EcosAppDef {

        val appRoot = ZipUtils.extractZip(data)
        val meta = Json.mapper.read(appRoot.getFile("meta.json"), EcosAppDef::class.java)
            ?: error("Incorrect application: ${Base64.getEncoder().encodeToString(data)}")

        val artifactsDir = appRoot.getDir("artifacts")
        var artifactsContentEntity: EcosContentEntity? = null
        if (artifactsDir != null) {
            artifactsContentEntity = ecosContentDao.upload(ZipUtils.writeZipAsBytes(artifactsDir))
        }

        var entity = dtoToEntity(meta)
        entity.artifactsDir = artifactsContentEntity
        entity = ecosAppRepo.save(entity)

        applicationsWatcherJob.forceUpdate("eapps", appToSource(entity))

        return entityToDto(entity)
    }

    fun save(app: EcosAppDef) : EcosAppDef {
        return entityToDto(ecosAppRepo.save(internalSave(app)))
    }

    private fun internalSave(app: EcosAppDef): EcosAppEntity {

        val artifactsSet = HashSet<String>()
        app.typeRefs.forEach { artifactsSet.add(typeRefToArtifactRef(it).id) }
        app.artifacts.forEach { artifactsSet.add(it.toString()) }

        ecosArtifactsService.setEcosAppFull(artifactsSet.map { ArtifactRef.valueOf(it) }, app.id)

        return dtoToEntity(app)
    }

    fun getById(id: String) : EcosAppDef? {
        val app = ecosAppRepo.findFirstByExtId(id) ?: return null
        return entityToDto(app)
    }

    fun getAll() : List<EcosAppDef> {
        return ecosAppRepo.findAll().map { entityToDto(it) }
    }

    fun delete(id: String) {
        ecosAppRepo.findFirstByExtId(id)?.let { ecosAppRepo.delete(it) }
        ecosArtifactsService.removeEcosApp(id)
    }

    fun getAppForArtifacts(list: List<RecordRef>) : Map<RecordRef, RecordRef> {
/*
        val result = mutableMapOf<RecordRef, RecordRef>()
        ecosAppContentRepo.findAllByArtifactIsIn(list.map { it.toString() }).forEach {
            val appId = it.app.extId
            if (appId != null) {
                result[RecordRef.valueOf(it.artifact)] = RecordRef.create("eapps", "ecos-app", appId)
            }
        }*/
        return emptyMap()//result
    }

    fun getAppData(id: String): ByteArray {

        val appDef = getById(id) ?: error("Invalid ECOS application ID: '$id'")

        val artifacts = mutableSetOf<RecordRef>()
        artifacts.addAll(appDef.artifacts)
        artifacts.addAll(appDef.typeRefs.map { typeRefToArtifactRef(it) })

        val rootDir = EcosMemDir(null, NameUtils.escape(id))
        val artifactsDir = rootDir.createDir("artifacts")

        for (ref in artifacts) {
            val artifactRef = ArtifactRef.valueOf(ref.id)
            val artifactRev = ecosArtifactsService.getLastArtifactRev(artifactRef)
            if (artifactRev != null) {
                ZipUtils.extractZip(
                    ByteArrayInputStream(artifactRev.data),
                    artifactsDir.getOrCreateDir(artifactRef.type)
                )
            }
        }

        rootDir.createFile("meta.json", Json.mapper.toPrettyString(appDef) ?: error("toPrettyString error"))

        return ZipUtils.writeZipAsBytes(rootDir)
    }

    private fun dtoToEntity(dto: EcosAppDef) : EcosAppEntity {

        val nullableEntity = ecosAppRepo.findFirstByExtId(dto.id)

        val entity = if (nullableEntity != null) {

            nullableEntity

        } else {

            val newEntity = EcosAppEntity()
            newEntity.extId = if (dto.id.isBlank()) {
                 UUID.randomUUID().toString()
            } else {
                dto.id
            }
            newEntity
        }

        entity.name = Json.mapper.toString(dto.name)
        entity.version = dto.version.toString()
        entity.typeRefs = Json.mapper.toString(dto.typeRefs)
        entity.artifacts = Json.mapper.toString(dto.artifacts)

        return entity
    }

    private fun entityToDto(entity: EcosAppEntity) : EcosAppDef {

        return EcosAppDef.create {
            id = entity.extId ?: ""
            name = Json.mapper.read(entity.name, MLText::class.java) ?: MLText()
            version = Version(entity.version ?: "1.0")
            typeRefs = DataValue.create(entity.typeRefs).asList(RecordRef::class.java)
            artifacts = DataValue.create(entity.artifacts).asList(RecordRef::class.java)
        }
    }

    private fun typeRefToArtifactRef(typeRef: RecordRef) : RecordRef {
        return RecordRef.create("eapps", EcosArtifactRecords.ID, "model/type$${typeRef.id}")
    }

    // AdditionalSourceProvider

    private fun appToSource(app: EcosAppEntity): ArtifactSourceInfo {
        return ArtifactSourceInfo.create {
            withKey(app.extId, ArtifactSourceType.ECOS_APP)
            withLastModified(app.lastModifiedDate)
        }
    }

    override fun getArtifactSources(): List<ArtifactSourceInfo> {
        return ecosAppRepo.findAllByArtifactsDirIsNotNull().map { appToSource(it) }
    }

    override fun getArtifacts(source: SourceKey,
                              types: List<TypeContext>,
                              since: Instant): Map<String, List<Any>> {

        val appEntity = ecosAppRepo.findFirstByExtId(source.id) ?: return emptyMap()
        val artifactsDirContent = appEntity.artifactsDir ?: return emptyMap()

        val artifactsDir = ZipUtils.extractZip(artifactsDirContent.data)

        return artifactService.readArtifacts(artifactsDir, types)
    }
}
