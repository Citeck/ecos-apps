package ru.citeck.ecos.apps.domain.ecosapp.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.apps.EcosAppsApp
import ru.citeck.ecos.apps.EcosAppsServiceFactory
import ru.citeck.ecos.apps.app.common.AppSystemArtifactPerms
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
import ru.citeck.ecos.apps.domain.ecosapp.api.records.EcosAppRecords
import ru.citeck.ecos.apps.domain.ecosapp.dto.EcosAppDef
import ru.citeck.ecos.apps.domain.ecosapp.repo.EcosAppEntity
import ru.citeck.ecos.apps.domain.ecosapp.repo.EcosAppRepo
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.Version
import ru.citeck.ecos.commons.data.entity.EntityMeta
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.commons.io.file.EcosFile
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.commons.utils.NameUtils
import ru.citeck.ecos.commons.utils.ZipUtils
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.api.constants.AppName
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
    private val jpaSearchConverterFactory: JpaSearchConverterFactory,
    private val perms: AppSystemArtifactPerms,
    private val workspaceService: WorkspaceService
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val CURRENT_WS_PLACEHOLDER = EcosArtifactsService.CURRENT_WS_PLACEHOLDER
    }

    private lateinit var searchConv: JpaSearchConverter<EcosAppEntity>

    @PostConstruct
    fun init() {
        searchConv = jpaSearchConverterFactory.createConverter(EcosAppEntity::class.java).build()
    }

    fun uploadZip(data: ByteArray, workspace: String): EcosAppDef {
        val appRoot = ZipUtils.extractZip(data)
        val meta = Json.mapper.read(appRoot.getFile("meta.json"), EcosAppDef::class.java)
            ?: error("Incorrect application: ${Base64.getEncoder().encodeToString(data)}")

        // imported ZIP is workspace-agnostic; assign it to the provided workspace
        // and rebind CURRENT_WS placeholders in refs to the target workspace sysId
        val targetWs = normalizeWorkspace(workspace)
        val targetWsSysId = if (targetWs.isNotEmpty()) workspaceService.getWorkspaceSystemId(targetWs) else ""
        val appMeta = meta.copy()
            .withWorkspace(targetWs)
            .withArtifacts(meta.artifacts.map { replaceCurrentWsPlaceholderInArtifactRef(it, targetWsSysId) })
            .withTypeRefs(
                meta.typeRefs.map {
                    it.withLocalId(workspaceService.replaceCurrentWsPlaceholderToWsPrefix(it.getLocalId(), targetWs))
                }
            )
            .build()

        perms.checkWrite(AppName.EAPPS, EcosAppRecords.ID, appMeta.id, targetWs)

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
                            artifactRefs.add(ArtifactRef.create(typeCtx.getId(), meta.id, targetWs))
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
            ecosArtifactsService.setEcosAppFull(artifactRefs, appMeta.id, targetWs)
        } else {
            log.info {
                "Application content doesn't change. App ID: '${appMeta.id}'"
            }
        }
        entity = ecosAppRepo.save(entity)

        applicationsWatcherJob.forceUpdate(EcosAppsApp.NAME, appToSource(entity))

        log.info { "Uploading of application '" + appMeta.id + "' completed" }

        return entityToDto(entity).entity
    }

    fun save(app: EcosAppDef): EcosAppDef {
        val appToSave = app.copy().withWorkspace(normalizeWorkspace(app.workspace)).build()

        perms.checkWrite(AppName.EAPPS, EcosAppRecords.ID, appToSave.id, appToSave.workspace)

        return entityToDto(ecosAppRepo.save(internalSave(appToSave))).entity
    }

    private fun internalSave(app: EcosAppDef): EcosAppEntity {

        val artifactsSet = HashSet<String>()
        app.typeRefs.forEach { artifactsSet.add(ArtifactUtils.typeRefToArtifactRef(it).getLocalId()) }
        app.artifacts.forEach { artifactsSet.add(it.getLocalId()) }

        ecosArtifactsService.setEcosAppFull(
            artifactsSet.map { ecosArtifactsService.parseArtifactRecordLocalId(it) },
            app.id,
            normalizeWorkspace(app.workspace)
        )

        return dtoToEntity(app)
    }

    fun getById(id: String, workspace: String): EcosAppDef? {
        return getByIdWithMeta(id, workspace)?.entity
    }

    fun getByIdWithMeta(id: String, workspace: String): EntityWithMeta<EcosAppDef>? {
        val app = ecosAppRepo.findFirstByExtIdAndWorkspace(id, normalizeWorkspace(workspace)) ?: return null
        return entityToDto(app)
    }

    fun getCount(predicate: Predicate, workspaces: List<String>): Long {
        return searchConv.getCount(ecosAppRepo, workspacesPredicate(predicate, workspaces))
    }

    fun getAll(
        predicate: Predicate,
        workspaces: List<String>,
        max: Int,
        skip: Int,
        sort: List<SortBy>
    ): List<EntityWithMeta<EcosAppDef>> {
        return searchConv.findAll(ecosAppRepo, workspacesPredicate(predicate, workspaces), max, skip, sort)
            .map { entityToDto(it) }
    }

    fun getAll(): List<EcosAppDef> {
        val sort = Sort.by(Sort.Order.desc("createdDate"))
        return ecosAppRepo.findAll(sort).map { entityToDto(it).entity }
    }

    fun delete(id: String, workspace: String) {
        val ws = normalizeWorkspace(workspace)
        perms.checkWrite(AppName.EAPPS, EcosAppRecords.ID, id, ws)
        ecosAppRepo.findFirstByExtIdAndWorkspace(id, ws)?.let { ecosAppRepo.delete(it) }
        ecosArtifactsService.removeEcosApp(id, ws)
    }

    private fun normalizeWorkspace(workspace: String?): String {
        if (workspace.isNullOrBlank() || workspaceService.isWorkspaceWithGlobalEntities(workspace)) {
            return ""
        }
        return workspace
    }

    private fun workspacesPredicate(predicate: Predicate, workspaces: List<String>): Predicate {
        val wsPredicate = workspaceService.buildAvailableWorkspacesPredicate(
            AuthContext.getCurrentRunAsAuth(),
            workspaces
        )
        return Predicates.and(predicate, wsPredicate)
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

    fun getAppData(id: String, workspace: String): ByteArray {

        val appDef = getById(id, workspace) ?: error("Invalid ECOS application ID: '$id'")

        // Collect every artifact ref of the app; each is in the record-id form `type$wsSysId:localId`
        // and is parsed below (via parseArtifactRecordLocalId) to fetch its data from the DB.
        val artifacts = mutableSetOf<EntityRef>()
        artifacts.addAll(appDef.artifacts)
        artifacts.addAll(appDef.typeRefs.map { ArtifactUtils.typeRefToArtifactRef(it) })

        val rootDir = EcosMemDir(null, NameUtils.escape(id))
        val artifactsDir = rootDir.createDir("artifacts")

        for (ref in artifacts) {
            val artifactRef = ecosArtifactsService.parseArtifactRecordLocalId(ref.getLocalId())
            val artifactRev = ecosArtifactsService.getLastArtifactRev(artifactRef, false)
            if (artifactRev != null) {
                ZipUtils.extractZip(
                    ByteArrayInputStream(artifactRev.data),
                    artifactsDir.getOrCreateDir(artifactRef.type)
                )
            } else {
                log.debug { "No lastRev for artifact '$artifactRef' on ecos-app '$id' export" }
            }
        }

        // Replace workspace prefix with CURRENT_WS placeholder in meta.json refs —
        // exported ecos-app is workspace-agnostic and the placeholder is rebound on import.
        val exportedDef = appDef.copy()
            .withWorkspace("")
            .withArtifacts(appDef.artifacts.map { artifactRefToCurrentWsPlaceholder(it) })
            .withTypeRefs(
                appDef.typeRefs.map {
                    it.withLocalId(workspaceService.replaceWsPrefixToCurrentWsPlaceholder(it.getLocalId()))
                }
            )
            .build()

        rootDir.createFile("meta.json", Json.mapper.toPrettyString(exportedDef) ?: error("toPrettyString error"))

        return ZipUtils.writeZipAsBytes(rootDir)
    }

    /**
     * Splits an artifact EntityRef local id (`type$rest`) and lets [transform] rewrite the `rest`
     * part; returning `null` from [transform] (or a malformed/no-`$` local id) leaves the ref as-is.
     * The `rest` is in the record-id form (`wsSysId:localId` / `localId` / `CURRENT_WS:localId`)
     * which ArtifactRef does not parse — hence the string surgery.
     */
    private inline fun rewriteArtifactRefRest(ref: EntityRef, transform: (rest: String) -> String?): EntityRef {
        val localId = ref.getLocalId()
        val dollarIdx = localId.indexOf('$')
        if (dollarIdx <= 0) {
            return ref
        }
        val type = localId.substring(0, dollarIdx)
        val rest = localId.substring(dollarIdx + 1)
        val newRest = transform(rest) ?: return ref
        return ref.withLocalId("$type\$$newRest")
    }

    /**
     * Replaces the workspace-system-id prefix with the CURRENT_WS placeholder in an artifact EntityRef,
     * e.g. "eapps/artifact@ui/form$wsSysId:my-form" → "eapps/artifact@ui/form$CURRENT_WS:my-form".
     * The placeholder is rebound to the target workspace on import.
     *
     * The first ':' in `rest` is the wsSysId/localId separator: artifact record ids produced by
     * [EcosArtifactsService.toArtifactRecordLocalId] escape any ':' inside the id itself, so a raw ':'
     * here can only be the wsSysId prefix separator (never part of the localId).
     */
    private fun artifactRefToCurrentWsPlaceholder(ref: EntityRef): EntityRef = rewriteArtifactRefRest(ref) { rest ->
        val colonIdx = rest.indexOf(':')
        if (colonIdx <= 0 || rest.substring(0, colonIdx) == CURRENT_WS_PLACEHOLDER) {
            null
        } else {
            "$CURRENT_WS_PLACEHOLDER:${rest.substring(colonIdx + 1)}"
        }
    }

    private fun replaceCurrentWsPlaceholderInArtifactRef(ref: EntityRef, targetWsSysId: String): EntityRef = rewriteArtifactRefRest(ref) { rest ->
        val placeholderPrefix = "$CURRENT_WS_PLACEHOLDER:"
        if (!rest.startsWith(placeholderPrefix)) {
            null
        } else {
            val localPart = rest.substring(placeholderPrefix.length)
            if (targetWsSysId.isEmpty()) localPart else "$targetWsSysId:$localPart"
        }
    }

    private fun dtoToEntity(dto: EcosAppDef): EcosAppEntity {

        val workspace = normalizeWorkspace(dto.workspace)
        val nullableEntity = ecosAppRepo.findFirstByExtIdAndWorkspace(dto.id, workspace)

        val entity = if (nullableEntity != null) {

            nullableEntity
        } else {

            val newEntity = EcosAppEntity()
            newEntity.extId = dto.id.ifBlank {
                UUID.randomUUID().toString()
            }
            newEntity.workspace = workspace
            newEntity
        }

        entity.name = Json.mapper.toString(dto.name)
        entity.version = dto.version.toString()
        entity.repositoryEndpoint = dto.repositoryEndpoint.toString()

        return entity
    }

    private fun entityToDto(entity: EcosAppEntity): EntityWithMeta<EcosAppDef> {

        val appArtifacts = ecosArtifactsService.getArtifactsByEcosApp(entity.extId, entity.workspace)
        val typeArtifactRefs = mutableListOf<EntityRef>()
        val otherArtifactRefs = mutableListOf<EntityRef>()

        appArtifacts.forEach {
            if (it.type == "model/type") {
                val wsSysId = if (it.workspace.isEmpty()) "" else ecosArtifactsService.toWsSysId(it.workspace)
                val typeLocalId = if (wsSysId.isEmpty()) it.id else "$wsSysId:${it.id}"
                typeArtifactRefs.add(ModelUtils.getTypeRef(typeLocalId))
            } else {
                otherArtifactRefs.add(
                    EntityRef.create(EcosAppsApp.NAME, EcosArtifactRecords.ID, ecosArtifactsService.toArtifactRecordLocalId(it))
                )
            }
        }

        val appDef = EcosAppDef.create {
            id = entity.extId
            name = Json.mapper.read(entity.name, MLText::class.java) ?: MLText()
            version = Version.valueOf(entity.version ?: "1.0")
            repositoryEndpoint = EntityRef.valueOf(entity.repositoryEndpoint)
            typeRefs = typeArtifactRefs
            artifacts = otherArtifactRefs
            workspace = entity.workspace
        }

        return EntityWithMeta(
            appDef,
            EntityMeta.create()
                .withCreated(entity.createdDate)
                .withModified(entity.lastModifiedDate)
                .withCreator(entity.createdBy)
                .withModifier(entity.lastModifiedBy)
                .build()
        )
    }

    // AdditionalSourceProvider

    private fun appToSource(app: EcosAppEntity): ArtifactSourceInfo {
        val lastModified = app.artifactsLastModifiedDate ?: app.artifactsDir?.createdDate ?: Instant.EPOCH
        // Encode workspace into the source id as a wsSysId prefix (platform convention —
        // `addWsPrefixToId` → `"${wsSysId}:${id}"`). Without this two ecos-apps with the same
        // extId in different workspaces would share one SourceKey and cross-contaminate on deploy.
        return ArtifactSourceInfo.create {
            withKey(
                workspaceService.addWsPrefixToId(app.extId, app.workspace),
                ArtifactSourceType.ECOS_APP
            )
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

        val idInWs = workspaceService.convertToIdInWs(source.id)
        val appEntity = ecosAppRepo.findFirstByExtIdAndWorkspace(
            idInWs.id,
            normalizeWorkspace(idInWs.workspace)
        ) ?: return EcosMemDir()
        val artifactsDirContent = appEntity.artifactsDir ?: return EcosMemDir()

        return ZipUtils.extractZip(artifactsDirContent.data)
    }
}
