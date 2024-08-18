package ru.citeck.ecos.apps.domain.git.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository
import org.eclipse.jgit.transport.PushResult
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.springframework.core.io.ClassPathResource
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
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.endpoints.lib.EcosEndpoints
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.authority.EcosAuthoritiesApi
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.lib.spring.context.auth.RunAsSystem
import java.io.File
import java.nio.file.Files

@Component
class EcosVcsObjectGitService(
    private val ecosAppService: EcosAppService,
    private val recordsService: RecordsService,
    private val ecosArtifactsService: EcosArtifactsService,
    private val ecosArtifactTypesService: EcosArtifactTypesService,
    private val ecosAuthoritiesApi: EcosAuthoritiesApi,
    private val artifactTypesService: EcosArtifactTypesService,
    private val artifactService: ArtifactService
) {

    companion object {
        private const val APP_GIT_TMP_DIR = "ecos-apps-git"
        private const val ARTIFACTS_PATH = "src/main/resources/app/artifacts"
        private const val META_FILE_PATH = "src/main/resources/app/meta.json"

        private const val RESOURCE_CITECK_APPLICATION_PATH = "generated-citeck-application/citeck-application/"
        private const val POM_FILE_NAME = "pom.xml"

        private val applicationRequiredFiles = listOf(POM_FILE_NAME, ".gitignore", ".gitattributes", ".editorconfig")

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

    @RunAsSystem
    fun getRepoBranchesForVcsObject(vcsObjectRef: EntityRef): List<String> {
        val appInfo = getAppInfo(vcsObjectRef) ?: return emptyList()
        return getRepoBranchesForEndpoint(appInfo.repositoryEndpoint)
    }

    @RunAsSystem
    fun getRepoBranchesForEndpoint(repoRef: EntityRef): List<String> {

        if (repoRef == EntityRef.EMPTY) {
            return emptyList()
        }

        val endpoint = EcosEndpoints.getEndpoint(repoRef.getLocalId())
        val basicData = endpoint.getCredentials()?.getBasicData() ?: return emptyList()

        val repoDesc = DfsRepositoryDescription()
        InMemoryRepository(repoDesc).use { inMemRepo ->
            val jgitCredential = UsernamePasswordCredentialsProvider(basicData.username, basicData.password)

            Git(inMemRepo).use { git ->

                git.fetch()
                    .setRemote(endpoint.getUrl())
                    .setCredentialsProvider(jgitCredential)
                    .setRefSpecs(RefSpec("+refs/heads/*:refs/heads/*"))
                    .call()

                return git.branchList().call().map {
                    it.name.replace("refs/heads/", "")
                }
            }
        }
    }

    fun canVcsObjectBeCommitted(objectRef: EntityRef): Boolean {
        return try {
            getAppInfo(objectRef)?.repositoryEndpoint?.isNotEmpty() ?: false
        } catch (e: Throwable) {
            false
        }
    }

    private fun getAppInfo(objectRef: EntityRef): AppInfo? {
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

        log.info { "Commit changes: $commit" }
        val appInfo = getAppInfo(commit.objectRef)
        require(appInfo != null) { "Could not get app info for object ref: ${commit.objectRef}" }

        val endpointRef = appInfo.repositoryEndpoint
        require(endpointRef.isNotEmpty()) { "Repository endpoint is required" }

        if (commit.newBranch) {
            AuthContext.runAsSystem {
                if (getRepoBranchesForEndpoint(endpointRef).contains(commit.branch)) {
                    error("Branch with name '${commit.branch}' already exists")
                }
            }
        }

        val endpoint = AuthContext.runAsSystem {
            EcosEndpoints.getEndpointOrNull(endpointRef.getLocalId())
        } ?: error("Endpoint instance is not found for ref $endpointRef")
        val basicData = AuthContext.runAsSystem {
            endpoint.getCredentials()?.getBasicData()
        } ?: error("Endpoint instance is found but credentials is empty. Endpoint ref: $endpointRef")

        var gitCloneFolder: File? = null
        try {
            val user = AuthContext.getCurrentUser()
            gitCloneFolder = createTempDirectory(commit, user)

            val jgitCredential = UsernamePasswordCredentialsProvider(basicData.username, basicData.password)

            val branchName = commit.branch.trim()
            val cloneFromBranch = if (commit.newBranch) {
                commit.newBranchFrom
            } else {
                commit.branch
            }.trim()

            Git.cloneRepository()
                .setURI(endpoint.getUrl())
                .setCredentialsProvider(jgitCredential)
                .setDirectory(gitCloneFolder)
                .setBranch(cloneFromBranch)
                .call()
                .use { git ->
                    if (commit.newBranch) {
                        git.checkout().setCreateBranch(true).setName(branchName).call()
                    }

                    writeEcosVcsObjectToDisk(gitCloneFolder, commit.objectRef)
                    initCiteckAppProjectStructureIfNotExists(gitCloneFolder, appInfo)

                    git.add()
                        .addFilepattern(".")
                        .call()

                    val userRef = ecosAuthoritiesApi.getAuthorityRef(AuthContext.getCurrentUser())
                    val userInfo = recordsService.getAtts(userRef, UserInfo::class.java)

                    val revCommit = git.commit()
                        .setAuthor("${userInfo.firstName} ${userInfo.lastName}", userInfo.email)
                        .setMessage(commit.commitMessage)
                        .call()

                    log.info { "Committed files as " + revCommit + " to repository at " + git.repository.directory }

                    pushChanges(git, jgitCredential)
                }
        } catch (e: Exception) {
            log.error(e) { "Error while committing changes" }
            throw e
        } finally {
            gitCloneFolder?.deleteRecursively()
        }
    }

    private fun createTempDirectory(commit: EcosVcsObjectCommit, user: String): File {
        val gitCloneFolder = Files.createTempDirectory(
            "$APP_GIT_TMP_DIR-$user-${commit.objectRef.getLocalId()}"
        ).toFile()
        gitCloneFolder.mkdirs()

        log.debug { "gitCloneFolder: ${gitCloneFolder.absolutePath}" }

        return gitCloneFolder
    }

    private fun pushChanges(git: Git, jgitCredential: UsernamePasswordCredentialsProvider) {
        val pushes = git.push()
            .setCredentialsProvider(jgitCredential)
            .call()
        for (result: PushResult in pushes) {
            for (update: RemoteRefUpdate in result.remoteUpdates) {
                log.info { "Having result: $update" }
                if (update.status != RemoteRefUpdate.Status.OK && update.status != RemoteRefUpdate.Status.UP_TO_DATE) {
                    throw RuntimeException("Push failed: ${update.status}")
                }
            }
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

    private fun initCiteckAppProjectStructureIfNotExists(projectDir: File, appInfo: AppInfo) {
        applicationRequiredFiles.forEach { fileName ->
            val targetFile = File(projectDir, fileName)
            if (!targetFile.exists()) {
                val resource = ClassPathResource("$RESOURCE_CITECK_APPLICATION_PATH$fileName")
                resource.inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                if (fileName == POM_FILE_NAME) {
                    val pomFile = File(projectDir, POM_FILE_NAME)
                    if (pomFile.exists()) {
                        val pomContent = pomFile.readText()
                        val modifiedPomContent = pomContent.replace(
                            Regex(
                                "(<project[^>]*>.*?<artifactId>)(.*?)(</artifactId>.*?</project>)",
                                RegexOption.DOT_MATCHES_ALL
                            ),
                            "$1${appInfo.id}$3"
                        )
                        pomFile.writeText(modifiedPomContent)
                    }
                }
            }
        }
    }

    private fun writeArtifactsToDir(baseDir: EcosFile?, targetDir: EcosFile, artifacts: Collection<EntityRef>) {

        val artifactRefsByType = HashMap<String, MutableList<ArtifactRef>>()
        artifacts.forEach { ref ->
            val artifactRef = ArtifactRef.valueOf(ref.getLocalId())
            artifactRefsByType.computeIfAbsent(artifactRef.type) { ArrayList() }.add(artifactRef)
        }

        for ((type, refs) in artifactRefsByType) {
            val typeCtx = artifactTypesService.getTypeContext(type)
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

    private data class UserInfo(
        @AttName("email")
        var email: String? = "",

        @AttName("firstName")
        var firstName: String? = "",

        @AttName("lastName")
        var lastName: String? = ""
    )

    private data class AppInfo(
        val id: String,
        val repositoryEndpoint: EntityRef = EntityRef.EMPTY
    )
}

data class EcosVcsObjectCommit(
    val objectRef: EntityRef,
    val branch: String,
    val commitMessage: String,
    val newBranch: Boolean,
    val newBranchFrom: String
) {

    init {
        require(branch.isNotBlank()) { "Branch name is required" }
        require(commitMessage.isNotBlank()) { "Commit message is required" }

        if (newBranch) {
            require(newBranchFrom.isNotBlank()) {
                "New branch from is required"
            }
        }
    }
}
