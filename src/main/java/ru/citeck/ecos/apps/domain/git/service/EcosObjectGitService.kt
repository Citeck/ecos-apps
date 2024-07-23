package ru.citeck.ecos.apps.domain.git.service

import mu.KotlinLogging
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository
import org.eclipse.jgit.transport.PushResult
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.domain.artifact.artifact.api.records.EcosArtifactRecords
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService
import ru.citeck.ecos.apps.domain.ecosapp.api.records.EcosAppRecords
import ru.citeck.ecos.apps.domain.ecosapp.service.EcosAppService
import ru.citeck.ecos.apps.domain.ecosapp.service.EcosAppUtils
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir
import ru.citeck.ecos.commons.io.file.std.EcosStdFile
import ru.citeck.ecos.commons.utils.NameUtils
import ru.citeck.ecos.commons.utils.ZipUtils
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
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

@Component
class EcosObjectGitService(
    private val ecosAppService: EcosAppService,
    private val ecosAppUtils: EcosAppUtils,
    private val recordsService: RecordsService,
    private val ecosArtifactsService: EcosArtifactsService,
    private val ecosArtifactTypesService: EcosArtifactTypesService,
    private val ecosAuthoritiesApi: EcosAuthoritiesApi
) {

    companion object {
        private const val APP_GIT_TMP_DIR = "ecos-apps-git"
        private const val ARTIFACTS_PATH = "src/main/resources/app/artifacts"

        private val log = KotlinLogging.logger {}
    }

    @RunAsSystem
    fun getRepoBranches(objectRef: EntityRef): List<String> {
        val repo = getEndpoint(objectRef)
        if (repo == EntityRef.EMPTY) {
            return emptyList()
        }

        val endpoint = EcosEndpoints.getEndpoint(repo.getLocalId())
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

    private fun getEndpoint(objectRef: EntityRef): EntityRef {
        return when (objectRef.getSourceId()) {
            EcosAppRecords.ID -> {
                val app = ecosAppService.getById(objectRef.getLocalId())
                app?.repositoryEndpoint ?: EntityRef.EMPTY
            }

            else -> {
                val artifactApp = getAppRefByObjectRef(objectRef)
                val app = ecosAppService.getById(artifactApp.getLocalId())

                return app?.repositoryEndpoint ?: EntityRef.EMPTY
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
        val artifactTypeId = ecosArtifactTypesService.getTypeIdForRecordRef(ref)
        val typeContext = ecosArtifactTypesService.getTypeContext(artifactTypeId)
            ?: error("Type context not found for $artifactTypeId")

        return EntityRef.create(
            AppName.EAPPS,
            EcosArtifactRecords.ID,
            "${typeContext.getId()}\$${ref.getLocalId()}"
        )
    }

    fun commitChanges(commit: EcosObjectCommit) {
        val user = AuthContext.getCurrentUser()
        val endpointRef = getEndpoint(commit.objectRef)
        require(endpointRef.isNotEmpty()) {
            "Repository endpoint is required"
        }

        val endpoint = AuthContext.runAsSystem {
            EcosEndpoints.getEndpointOrNull(endpointRef.getLocalId())
        } ?: return
        val basicData = AuthContext.runAsSystem { endpoint.getCredentials()?.getBasicData() } ?: return

        var gitCloneFolder: File? = null
        try {
            gitCloneFolder = Files.createTempDirectory(
                "$APP_GIT_TMP_DIR-$user-${commit.objectRef.getLocalId()}"
            ).toFile()
            gitCloneFolder.mkdirs()

            log.debug { "gitCloneFolder: ${gitCloneFolder.absolutePath}" }

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
                        git.checkout().setCreateBranch(true).setName(branchName)
                            .call()
                    }

                    writeEcosObjectToDisk(gitCloneFolder, commit.objectRef)

                    git.add()
                        .addFilepattern(".")
                        .call()

                    val userRef = ecosAuthoritiesApi.getAuthorityRef(user)
                    val userInfo = recordsService.getAtts(userRef, UserInfo::class.java)

                    val revCommit = git.commit()
                        .setAuthor("${userInfo.firstName} ${userInfo.lastName}", userInfo.email)
                        .setMessage(commit.commitMessage)
                        .call()

                    println("Committed files as " + revCommit + " to repository at " + git.repository.directory)

                    val pushes = git.push()
                        .setCredentialsProvider(jgitCredential)
                        .call()


                    val results: Iterable<PushResult> = pushes
                    for (r: PushResult in results) {
                        for (update: RemoteRefUpdate in r.remoteUpdates) {
                            println("Having result: $update")
                            if (update.status != RemoteRefUpdate.Status.OK && update.status != RemoteRefUpdate.Status.UP_TO_DATE) {
                                val errorMessage = "Push failed: " + update.status
                                throw RuntimeException(errorMessage)
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            log.error(e) { "Error while committing changes" }
            throw e
        } finally {
            gitCloneFolder?.deleteRecursively()
        }
    }

    private fun writeEcosObjectToDisk(folder: File, ecosObject: EntityRef) {
        // TODO: check that artifact really changed by ArtifactService -> compareArtifacts, isTypeComparable methods

        // TODO: bug - why some artifacts dont writes? For example, ui journals

        when (ecosObject.getSourceId()) {
            EcosAppRecords.ID -> {
                writeAppArtifactsToDisk(folder, ecosObject)
            }

            else -> {
                val explicitArtifactRef = getExplicitArtifactRef(ecosObject)
                writeArtifactToDisk(folder, explicitArtifactRef)
            }
        }

    }

    private fun writeAppArtifactsToDisk(folder: File, appRef: EntityRef) {
        val id = appRef.getLocalId()
        val appDef = ecosAppService.getById(id) ?: error("Invalid ECOS application ID: '$id'")

        val rootMemDir = ecosAppUtils.writeAppArtifactsToMemDir(appDef, id, ARTIFACTS_PATH)

        rootMemDir.createFile(
            "meta.json", """
            {
              "repositoryEndpoint": "${appDef.repositoryEndpoint}"
            }
        """.trimIndent()
        )

        val sys = EcosStdFile(folder)
        sys.copyFilesFrom(rootMemDir)
    }

    private fun writeArtifactToDisk(folder: File, artifactEntityRef: EntityRef) {
        val artifactId = artifactEntityRef.getLocalId()

        val rootMemDir = EcosMemDir(null, NameUtils.escape(artifactEntityRef.getLocalId()))
        val artifactsDir = rootMemDir.createDir(ARTIFACTS_PATH)

        val artifactRef = ArtifactRef.valueOf(artifactId)
        val artifactRev = ecosArtifactsService.getLastArtifactRev(artifactRef, false)
            ?: error("Artifact revision not found for $artifactId")

        ZipUtils.extractZip(
            ByteArrayInputStream(artifactRev.data),
            artifactsDir.getOrCreateDir(artifactRef.type)
        )

        val sys = EcosStdFile(folder)
        sys.copyFilesFrom(rootMemDir)
    }

    private data class UserInfo(
        @AttName("email")
        var email: String? = "",

        @AttName("firstName")
        var firstName: String? = "",

        @AttName("lastName")
        var lastName: String? = ""
    )
}

data class EcosObjectCommit(
    val objectRef: EntityRef,
    val branch: String,
    val commitMessage: String,
    val newBranch: Boolean,
    val newBranchFrom: String
) {

    init {
        require(branch.isNotBlank()) {
            "Branch name is required"
        }
        require(commitMessage.isNotBlank()) {
            "Commit message is required"
        }

        if (newBranch) {
            require(newBranchFrom.isNotBlank()) {
                "New branch from is required"
            }
        }
    }

}
