package ru.citeck.ecos.apps.domain.ecosapp.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.artifact.type.TypeContext
import ru.citeck.ecos.apps.domain.artifact.api.records.EcosArtifactRecords
import ru.citeck.ecos.apps.domain.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity
import ru.citeck.ecos.apps.domain.content.service.EcosContentDao
import ru.citeck.ecos.apps.domain.ecosapp.dto.EcosAppDef
import ru.citeck.ecos.apps.domain.ecosapp.repo.EcosAppArtifactEntity
import ru.citeck.ecos.apps.domain.ecosapp.repo.EcosAppArtifactRepo
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.Version
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.commons.utils.NameUtils
import ru.citeck.ecos.commons.utils.ZipUtils
import ru.citeck.ecos.records2.RecordRef
import java.io.ByteArrayInputStream
import java.util.*

@Transactional
@Service("ecosAppArtifactService")
open class EcosAppArtifactService(
    private val ecosAppRepo: EcosAppArtifactRepo,
    private val ecosArtifactsService: EcosArtifactsService,
    private val ecosContentDao: EcosContentDao
) {

    open fun uploadAllArtifactsForTypes(types: List<TypeContext>) {

        ecosAppRepo.findAll().forEach {
            val artifactsDirContent = it.artifactsDir
            if (artifactsDirContent != null ) {
                val artifactsDir = ZipUtils.extractZip(artifactsDirContent.data)
                //ecosArtifactsService.uploadEcosAppArtifacts(it.extId, artifactsDir, types)
            }
        }
    }

    open fun upload(data: ByteArray) : EcosAppDef {

        val appRoot = ZipUtils.extractZip(data)
        val meta = Json.mapper.read(appRoot.getFile("meta.json"), EcosAppDef::class.java)
            ?: error("Incorrect application: ${Base64.getEncoder().encodeToString(data)}")

        val artifactsDir = appRoot.getDir("artifacts")
        var artifactsContentEntity: EcosContentEntity? = null
        if (artifactsDir != null) {
            //ecosArtifactsService.uploadEcosAppArtifacts(meta.id, artifactsDir)
            artifactsContentEntity = ecosContentDao.upload(ZipUtils.writeZipAsBytes(artifactsDir))
        }

        val entity = internalSave(meta)
        entity.artifactsDir = artifactsContentEntity

        return entityToDto(ecosAppRepo.save(entity))
    }

    open fun save(app: EcosAppDef) : EcosAppDef {
        return entityToDto(ecosAppRepo.save(internalSave(app)))
    }

    private fun internalSave(app: EcosAppDef): EcosAppArtifactEntity {

        val artifactsSet = HashSet<String>()
        app.typeRefs.forEach { artifactsSet.add(typeRefToArtifactRef(it).id) }
        app.artifacts.forEach { artifactsSet.add(it.toString()) }

        ecosArtifactsService.setEcosAppFull(artifactsSet.map { ArtifactRef.valueOf(it) }, app.id)

        val entity = dtoToEntity(app)
        entity.artifactsDir = null

        return entity
    }

    open fun getById(id: String) : EcosAppDef? {
        val app = ecosAppRepo.findFirstByExtId(id) ?: return null
        return entityToDto(app)
    }

    open fun getAll() : List<EcosAppDef> {
        return ecosAppRepo.findAll().map { entityToDto(it) }
    }

    open fun delete(id: String) {
        ecosAppRepo.findFirstByExtId(id)?.let { ecosAppRepo.delete(it) }
        ecosArtifactsService.removeEcosApp(id)
    }

    open fun getAppForArtifacts(list: List<RecordRef>) : Map<RecordRef, RecordRef> {
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

    open fun getAppData(id: String): ByteArray {

        val appDef = getById(id) ?: error("Invalid ECOS application ID: '$id'")

        val artifacts = mutableSetOf<RecordRef>()
        artifacts.addAll(appDef.artifacts)
        artifacts.addAll(appDef.typeRefs.map { typeRefToArtifactRef(it) })

        val rootDir = EcosMemDir(null, NameUtils.escape(id))
        val artifactsDir = rootDir.createDir("artifacts")

        for (ref in artifacts) {
            val moduleRef = ArtifactRef.valueOf(ref.id)
            val moduleRev = ecosArtifactsService.getLastArtifactRev(moduleRef)
            if (moduleRev != null) {
                ZipUtils.extractZip(ByteArrayInputStream(moduleRev.data), artifactsDir.getOrCreateDir(moduleRef.type))
            }
        }

        rootDir.createFile("meta.json", Json.mapper.toPrettyString(appDef) ?: error("toPrettyString error"))

        return ZipUtils.writeZipAsBytes(rootDir)
    }

    private fun dtoToEntity(dto: EcosAppDef) : EcosAppArtifactEntity {

        val nullableEntity = ecosAppRepo.findFirstByExtId(dto.id)

        val entity = if (nullableEntity != null) {

            nullableEntity

        } else {

            val newEntity = EcosAppArtifactEntity()
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

    private fun entityToDto(entity: EcosAppArtifactEntity) : EcosAppDef {

        return EcosAppDef.create {
            id = entity.extId ?: ""
            name = Json.mapper.read(entity.name, MLText::class.java) ?: MLText()
            version = Version(entity.version ?: "0")
            typeRefs = DataValue.create(entity.typeRefs).asList(RecordRef::class.java)
            artifacts = DataValue.create(entity.artifacts).asList(RecordRef::class.java)
        }
    }

    private fun typeRefToArtifactRef(typeRef: RecordRef) : RecordRef {
        return RecordRef.create("eapps", EcosArtifactRecords.ID, "model/type$${typeRef.id}")
    }
}
