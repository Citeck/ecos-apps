package ru.citeck.ecos.apps.domain.ecosapp.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Lazy
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.apps.EcosAppsApp
import ru.citeck.ecos.apps.EcosAppsServiceFactory
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceInfo
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType
import ru.citeck.ecos.apps.app.domain.artifact.source.SourceKey
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.artifact.type.TypeContext
import ru.citeck.ecos.apps.domain.artifact.application.job.ApplicationsWatcherJob
import ru.citeck.ecos.apps.domain.artifact.artifact.api.records.EcosArtifactRecords
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity
import ru.citeck.ecos.apps.domain.content.service.EcosContentDao
import ru.citeck.ecos.apps.domain.ecosapp.dto.EcosAppDef
import ru.citeck.ecos.apps.domain.ecosapp.repo.EcosAppEntity
import ru.citeck.ecos.apps.domain.ecosapp.repo.EcosAppRepo
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.Version
import ru.citeck.ecos.commons.io.file.EcosFile
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.commons.utils.NameUtils
import ru.citeck.ecos.commons.utils.ZipUtils
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverter
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverterFactory
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.*

@Service
@Transactional
class EcosAppService(
    private val typesService: EcosArtifactTypesService,
    private val ecosAppsServiceFactory: EcosAppsServiceFactory,
    private val ecosAppRepo: EcosAppRepo,
    private val ecosArtifactsService: EcosArtifactsService,
    private val ecosContentDao: EcosContentDao,
    private val applicationsWatcherJob: ApplicationsWatcherJob,
    private val jpaSearchConverterFactory: JpaSearchConverterFactory
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    private lateinit var searchConv: JpaSearchConverter<EcosAppEntity>

    @PostConstruct
    fun init() {
        searchConv = jpaSearchConverterFactory.createConverter(EcosAppEntity::class.java).build()
    }

    fun uploadZip(data: ByteArray): EcosAppDef {

        val appRoot = ZipUtils.extractZip(data)
        val appMeta = Json.mapper.read(appRoot.getFile("meta.json"), EcosAppDef::class.java)
            ?: error("Incorrect application: ${Base64.getEncoder().encodeToString(data)}")

        log.info { "Upload application '" + appMeta.id + "'" }

        val artifactsDir = appRoot.getDir("artifacts")
        val artifactRefs = mutableListOf<ArtifactRef>()

        if (artifactsDir != null) {
            val types = typesService.allTypesCtx.map {
                it.getTypeContext()
            }
            val artifactsData = ecosAppsServiceFactory.artifactService.readArtifacts(artifactsDir, types)
            types.forEach { typeCtx ->
                val artifacts = artifactsData[typeCtx.getId()]
                if (artifacts != null && artifacts.isNotEmpty()) {
                    artifacts.forEach {
                        val meta = ecosAppsServiceFactory.artifactService.getArtifactMeta(typeCtx, it)
                        if (meta != null) {
                            artifactRefs.add(ArtifactRef.create(typeCtx.getId(), meta.id))
                        }
                    }
                }
            }
            val ecosAppIdByArtifactRef = ecosArtifactsService.getEcosAppIdByArtifactRef(artifactRefs)
            val invalidArtifactsByOwner = mutableMapOf<String, MutableList<ArtifactRef>>()

            ecosAppIdByArtifactRef.forEach { (artifactRef, ecosAppId) ->
                if (ecosAppId.isNotBlank() && ecosAppId != appMeta.id) {
                    invalidArtifactsByOwner.computeIfAbsent(ecosAppId) { mutableListOf() }.add(artifactRef)
                }
            }
            if (invalidArtifactsByOwner.isNotEmpty()) {
                error(
                    "You can't upload application '${appMeta.id}' " +
                        "with artifacts which is belong " +
                        "to other ECOS applications: $invalidArtifactsByOwner"
                )
            }
        }

        var artifactsContentEntity: EcosContentEntity? = null
        if (artifactsDir != null) {
            artifactsContentEntity = ecosContentDao.upload(ZipUtils.writeZipAsBytes(artifactsDir))
        }

        var entity = dtoToEntity(appMeta)
        if (entity.artifactsDir?.id != artifactsContentEntity?.id) {
            log.info {
                "Application content changed. App ID: '${appMeta.id}' " +
                    "New content id: ${artifactsContentEntity?.id}"
            }
            entity.artifactsDir = artifactsContentEntity
            entity.artifactsLastModifiedDate = Instant.now()
            ecosArtifactsService.setEcosAppFull(artifactRefs, appMeta.id)
        } else {
            log.info {
                "Application content doesn't change. App ID: '${appMeta.id}'"
            }
        }
        entity = ecosAppRepo.save(entity)

        applicationsWatcherJob.forceUpdate(EcosAppsApp.NAME, appToSource(entity))

        log.info { "Uploading of application '" + appMeta.id + "' completed" }

        return entityToDto(entity)
    }

    fun save(app: EcosAppDef): EcosAppDef {
        return entityToDto(ecosAppRepo.save(internalSave(app)))
    }

    private fun internalSave(app: EcosAppDef): EcosAppEntity {

        val artifactsSet = HashSet<String>()
        app.typeRefs.forEach { artifactsSet.add(ArtifactUtils.typeRefToArtifactRef(it).getLocalId()) }
        app.artifacts.forEach { artifactsSet.add(it.getLocalId()) }

        ecosArtifactsService.setEcosAppFull(artifactsSet.map { ArtifactRef.valueOf(it) }, app.id)

        return dtoToEntity(app)
    }

    fun getById(id: String): EcosAppDef? {
        val app = ecosAppRepo.findFirstByExtId(id) ?: return null
        return entityToDto(app)
    }

    fun getCount(predicate: Predicate): Long {
        return searchConv.getCount(ecosAppRepo, predicate)
    }

    fun getAll(predicate: Predicate, max: Int, skip: Int, sort: List<SortBy>): List<EcosAppDef> {
        return searchConv.findAll(ecosAppRepo, predicate, max, skip, sort).map { entityToDto(it) }
    }

    fun getAll(): List<EcosAppDef> {
        val sort = Sort.by(Sort.Order.desc("createdDate"))
        return ecosAppRepo.findAll(sort).map { entityToDto(it) }
    }

    fun delete(id: String) {
        ecosAppRepo.findFirstByExtId(id)?.let { ecosAppRepo.delete(it) }
        ecosArtifactsService.removeEcosApp(id)
    }

    fun getAppForArtifacts(list: List<EntityRef>): Map<EntityRef, EntityRef> {
/*
        val result = mutableMapOf<EntityRef, EntityRef>()
        ecosAppContentRepo.findAllByArtifactIsIn(list.map { it.toString() }).forEach {
            val appId = it.app.extId
            if (appId != null) {
                result[EntityRef.valueOf(it.artifact)] = EntityRef.create("eapps", "ecos-app", appId)
            }
        }*/
        return emptyMap() // result
    }

    fun getAppData(id: String): ByteArray {

        val appDef = getById(id) ?: error("Invalid ECOS application ID: '$id'")

        val artifacts = mutableSetOf<EntityRef>()
        artifacts.addAll(appDef.artifacts)
        artifacts.addAll(appDef.typeRefs.map { ArtifactUtils.typeRefToArtifactRef(it) })

        val rootDir = EcosMemDir(null, NameUtils.escape(id))
        val artifactsDir = rootDir.createDir("artifacts")

        for (ref in artifacts) {
            val artifactRef = ArtifactRef.valueOf(ref.getLocalId())
            val artifactRev = ecosArtifactsService.getLastArtifactRev(artifactRef, false)
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

    private fun dtoToEntity(dto: EcosAppDef): EcosAppEntity {

        val nullableEntity = ecosAppRepo.findFirstByExtId(dto.id)

        val entity = if (nullableEntity != null) {

            nullableEntity
        } else {

            val newEntity = EcosAppEntity()
            newEntity.extId = dto.id.ifBlank {
                UUID.randomUUID().toString()
            }
            newEntity
        }

        entity.name = Json.mapper.toString(dto.name)
        entity.version = dto.version.toString()
        entity.repositoryEndpoint = dto.repositoryEndpoint.toString()

        return entity
    }

    private fun entityToDto(entity: EcosAppEntity): EcosAppDef {

        val appArtifacts = ecosArtifactsService.getArtifactsByEcosApp(entity.extId)
        val typeArtifactRefs = mutableListOf<EntityRef>()
        val otherArtifactRefs = mutableListOf<EntityRef>()

        appArtifacts.forEach {
            if (it.type == "model/type") {
                typeArtifactRefs.add(ModelUtils.getTypeRef(it.id))
            } else {
                otherArtifactRefs.add(EntityRef.create(EcosAppsApp.NAME, EcosArtifactRecords.ID, it.toString()))
            }
        }

        return EcosAppDef.create {
            id = entity.extId
            name = Json.mapper.read(entity.name, MLText::class.java) ?: MLText()
            version = Version.valueOf(entity.version ?: "1.0")
            repositoryEndpoint = EntityRef.valueOf(entity.repositoryEndpoint)
            typeRefs = typeArtifactRefs
            artifacts = otherArtifactRefs
        }
    }

    // AdditionalSourceProvider

    private fun appToSource(app: EcosAppEntity): ArtifactSourceInfo {
        val lastModified = app.artifactsLastModifiedDate ?: app.artifactsDir?.createdDate ?: Instant.EPOCH
        return ArtifactSourceInfo.create {
            withKey(app.extId, ArtifactSourceType.ECOS_APP)
            withLastModified(lastModified)
        }
    }

    fun getArtifactSources(): List<ArtifactSourceInfo> {
        return ecosAppRepo.findAllByArtifactsDirIsNotNull().map { appToSource(it) }
    }

    fun getArtifactsDir(
        source: SourceKey,
        types: List<TypeContext>,
        since: Instant
    ): EcosFile {

        val appEntity = ecosAppRepo.findFirstByExtId(source.id) ?: return EcosMemDir()
        val artifactsDirContent = appEntity.artifactsDir ?: return EcosMemDir()

        return ZipUtils.extractZip(artifactsDirContent.data)
    }
}
