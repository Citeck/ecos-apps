package ru.citeck.ecos.apps.domain.git.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.artifact.ArtifactService
import ru.citeck.ecos.apps.domain.artifact.artifact.api.records.EcosArtifactRecords
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.ArtifactRevSourceType
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService
import ru.citeck.ecos.apps.domain.ecosapp.api.records.EcosAppRecords
import ru.citeck.ecos.apps.domain.ecosapp.service.ArtifactUtils
import ru.citeck.ecos.apps.domain.ecosapp.service.EcosAppService
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.io.file.EcosFile
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir
import ru.citeck.ecos.commons.io.file.std.EcosStdFile
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.commons.utils.NameUtils
import ru.citeck.ecos.config.lib.consumer.bean.EcosConfig
import ru.citeck.ecos.ent.git.service.AppInfo
import ru.citeck.ecos.ent.git.service.EcosVcsObjectCommit
import ru.citeck.ecos.ent.git.service.EcosVcsObjectGitService
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import java.io.File

@Component
class AppsGitService(
    private val ecosAppService: EcosAppService,
    private val recordsService: RecordsService,
    private val ecosArtifactsService: EcosArtifactsService,
    private val ecosArtifactTypesService: EcosArtifactTypesService,
    private val artifactService: ArtifactService,
    private val ecosVcsObjectGitService: EcosVcsObjectGitService
) {

    companion object {
        private const val ARTIFACTS_PATH = "src/main/resources/app/artifacts"
        private const val META_FILE_PATH = "src/main/resources/app/meta.json"

        private val log = KotlinLogging.logger {}
    }

    @EcosConfig("ecos-vcs-allowed-base-branches")
    private lateinit var allowedBaseBranches: String

    @EcosConfig("ecos-vcs-allowed-branches-to-commit")
    private lateinit var allowedBranchesToCommit: String

    fun getAllowedBaseBranches(branches: List<String>): List<String> {
        return getFilteredBranches(branches, allowedBaseBranches)
    }

    fun getAllowedBranchesToCommit(branches: List<String>): List<String> {
        return getFilteredBranches(branches, allowedBranchesToCommit)
    }

    private fun getFilteredBranches(branches: List<String>, regex: String): List<String> {
        if (regex.isBlank() || branches.isEmpty()) {
            return emptyList()
        }
        val filterRegex = regex.toRegex(RegexOption.IGNORE_CASE)
        return branches.filter { filterRegex.matches(it) }
    }

    fun canVcsObjectBeCommitted(objectRef: EntityRef): Boolean {
        return try {
            getAppInfo(objectRef)?.repositoryEndpoint?.isNotEmpty() ?: false && ecosVcsObjectGitService.featureAllowed()
        } catch (e: Throwable) {
            false
        }
    }

    fun getAppInfo(objectRef: EntityRef): AppInfo? {
        return when (objectRef.getSourceId()) {
            EcosAppRecords.ID -> {
                val app = ecosAppService.getById(objectRef.getLocalId())
                val repoEndpoint = app?.repositoryEndpoint ?: EntityRef.EMPTY

                AppInfo(
                    objectRef.getLocalId(),
                    repoEndpoint
                )
            }

            else -> {
                val artifactApp = getAppRefByObjectRef(objectRef)
                val app = ecosAppService.getById(artifactApp.getLocalId()) ?: return null
                val repositoryEndpoint = app.repositoryEndpoint

                AppInfo(
                    app.id,
                    repositoryEndpoint
                )
            }
        }
    }

    private fun getAppRefByObjectRef(artifactRef: EntityRef): EntityRef {
        val explicitArtifactRef = getExplicitArtifactRef(artifactRef)

        return recordsService.getAtt(
            explicitArtifactRef,
            EcosArtifactRecords.ECOS_APP_REF_ATTRIBUTE + ScalarType.ID_SCHEMA
        ).asText().toEntityRef()
    }

    private fun getExplicitArtifactRef(ref: EntityRef): EntityRef {

        val fixedRef = if (ref.getAppName() == AppName.EMODEL && ref.getSourceId() == "types-repo") {
            ref.withSourceId("type")
        } else {
            ref
        }

        val artifactTypeId = ecosArtifactTypesService.getTypeIdForRecordRef(fixedRef)

        if (artifactTypeId.isBlank()) {
            error(
                "Artifact type can't be calculated for ref '$ref'. " +
                    "Please add sourceId in eapps/types/**/type.yml"
            )
        }

        val typeContext = ecosArtifactTypesService.getTypeContext(artifactTypeId)
            ?: error("Type context not found for $artifactTypeId")

        return EntityRef.create(
            AppName.EAPPS,
            EcosArtifactRecords.ID,
            "${typeContext.getId()}\$${ref.getLocalId()}"
        )
    }

    fun commitChanges(commit: EcosVcsObjectCommit) {
        val appInfo = getAppInfo(commit.objectRef) ?: error("App info not found for objectRef: ${commit.objectRef}")
        ecosVcsObjectGitService.commitChanges(commit, appInfo) { folder, objectRef ->
            writeEcosVcsObjectToDisk(folder, objectRef)
        }
    }

    private fun writeEcosVcsObjectToDisk(folder: File, ecosVcsObject: EntityRef) {

        val baseDir = EcosStdFile(folder)
        val rootMemDir = EcosMemDir(null, NameUtils.escape(ecosVcsObject.getLocalId()))
        val artifactsDir = rootMemDir.createDir(ARTIFACTS_PATH)

        when (ecosVcsObject.getSourceId()) {
            EcosAppRecords.ID -> {

                val id = ecosVcsObject.getLocalId()
                val appDef = ecosAppService.getById(id) ?: error("Invalid ECOS application ID: '$id'")
                val artifacts = mutableSetOf<EntityRef>()

                artifacts.addAll(appDef.artifacts)
                artifacts.addAll(appDef.typeRefs.map { ArtifactUtils.typeRefToArtifactRef(it) })

                writeArtifactsToDir(baseDir.getDir(ARTIFACTS_PATH), artifactsDir, artifacts)

                val baseMeta = baseDir.getFile(META_FILE_PATH)?.let {
                    Json.mapper.read(it, DataValue::class.java)
                } ?: DataValue.createObj()
                baseMeta["repositoryEndpoint"] = appDef.repositoryEndpoint
                rootMemDir.createFile(META_FILE_PATH) {
                    it.write(Json.mapper.toPrettyStringNotNull(baseMeta).toByteArray(Charsets.UTF_8))
                }
            }

            else -> {
                val explicitArtifactRef = getExplicitArtifactRef(ecosVcsObject)
                writeArtifactsToDir(baseDir.getDir(ARTIFACTS_PATH), artifactsDir, listOf(explicitArtifactRef))
            }
        }

        baseDir.copyFilesFrom(rootMemDir)
    }

    private fun writeArtifactsToDir(baseDir: EcosFile?, targetDir: EcosFile, artifacts: Collection<EntityRef>) {

        val artifactRefsByType = HashMap<String, MutableList<ArtifactRef>>()
        artifacts.forEach { ref ->
            val artifactRef = ArtifactRef.valueOf(ref.getLocalId())
            artifactRefsByType.computeIfAbsent(artifactRef.type) { ArrayList() }.add(artifactRef)
        }

        for ((type, refs) in artifactRefsByType) {
            val typeCtx = ecosArtifactTypesService.getTypeContext(type)
            if (typeCtx == null) {
                log.warn { "Unknown artifact type: $type" }
                continue
            }

            val artifactsToWrite = ArrayList<Any>()
            for (ref in refs) {
                val lastArtifact = ecosArtifactsService.getLastArtifact(ref, false)
                if (lastArtifact == null) {
                    log.warn { "Artifact is null for ref $ref" }
                    continue
                }
                if (lastArtifact.source.type != ArtifactRevSourceType.ECOS_APP) {
                    artifactsToWrite.add(lastArtifact.data)
                }
            }
            if (artifactsToWrite.isNotEmpty()) {
                artifactService.writeArtifactsDiff(baseDir, targetDir, typeCtx.getTypeContext(), artifactsToWrite)
            }
        }
    }
}
