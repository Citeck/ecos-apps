package ru.citeck.ecos.apps.domain.git.api.records

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.domain.git.service.AppsGitService
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.ent.git.service.EcosVcsObjectCommit
import ru.citeck.ecos.ent.git.service.EcosVcsObjectGitService
import ru.citeck.ecos.ent.git.service.VcsConnectionResult
import ru.citeck.ecos.ent.git.service.VcsStatus
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.value.AttValue
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef

@Component
class GitRecords(
    private val appsGitService: AppsGitService,
    private val ecosVcsObjectGitService: EcosVcsObjectGitService
) : AbstractRecordsDao(), RecordsQueryDao, RecordAttsDao, RecordMutateDao {

    companion object {
        const val ID = "git"

        private const val ACTION_ATT = "action"
        private const val ACTION_COMMIT = "COMMIT"
    }

    override fun getId(): String {
        return ID
    }

    override fun queryRecords(recsQuery: RecordsQuery): GitRecordAtts? {
        if (recsQuery.language == "repo-connection-status") {
            val query = recsQuery.getQuery(RepoConnectionStatusQuery::class.java)
            val connectionStatus = AuthContext.runAsSystem {
                ecosVcsObjectGitService.checkConnection(query.repository)
            }

            return GitRecordAtts(query.repository, connectionStatus)
        }

        return null
    }

    override fun mutate(record: LocalRecordAtts): String {

        if (AuthContext.isRunAsSystemOrAdmin().not()) {
            error("Only system or admin can use git integration")
        }

        val action = record.getAtt(ACTION_ATT).asText()
        if (action != ACTION_COMMIT) {
            error("Unsupported mutate action: $action")
        }

        val isNewBranch = record.getAtt("newBranch").asBoolean()
        val branchName = if (isNewBranch) {
            "ecos/" + record.getAtt("newBranchName").asText()
        } else {
            record.getAtt("branch").asText()
        }

        val objectCommit = EcosVcsObjectCommit(
            objectRef = record.id.toEntityRef(),
            branch = branchName,
            commitMessage = record.getAtt("commitMessage").asText(),
            newBranch = isNewBranch,
            newBranchFrom = record.getAtt("newBranchFrom").asText()
        )

        appsGitService.commitChanges(objectCommit)

        return record.id
    }

    override fun getRecordAtts(recordId: String): Any? {
        return GitRecordAtts(recordId.toEntityRef())
    }

    @Suppress("UNUSED")
    inner class GitRecordAtts(
        private val ecosVcsObject: EntityRef,
        private val connectionResult: VcsConnectionResult = VcsConnectionResult(VcsStatus.UNDEFINED)
    ) {
        var id = ecosVcsObject.toString()
        var objectRepo = RepoMeta(ecosVcsObject)

        var connectionResultMsgHtml: String = let {
            val color = when (connectionResult.status) {
                VcsStatus.OK -> "green"
                VcsStatus.UNDEFINED -> "orange"
                VcsStatus.REPO_EMPTY -> "orange"
                else -> "red"
            }

            // TODO: msg localisation
            val msg = createHTML()
                .html {
                    body {
                        div {
                            p {
                                h6 {
                                    +"Статус подключения к репозиторию"
                                }
                                p {
                                    style = "color: $color;"
                                    +connectionResult.status.message
                                    if (connectionResult.message.isNotBlank()) {
                                        br()
                                        +connectionResult.message
                                    }
                                }
                            }
                        }
                    }
                }

            msg
        }

        fun getCanVcsObjectBeCommitted(): Boolean {
            return appsGitService.canVcsObjectBeCommitted(ecosVcsObject)
        }
    }

    inner class RepoMeta(
        private val ecosVcsObject: EntityRef
    ) : AttValue {

        private val allBranches by lazy {
            val appInfo = appsGitService.getAppInfo(ecosVcsObject) ?: return@lazy emptyList()
            ecosVcsObjectGitService.getRepoBranchesForEndpoint(appInfo.repositoryEndpoint)
        }

        override fun getAtt(name: String): Any? {
            return when (name) {
                "allowedBranchesToCommit" -> appsGitService.getAllowedBranchesToCommit(allBranches)
                "allowedBaseBranches" -> appsGitService.getAllowedBaseBranches(allBranches)
                else -> null
            }
        }
    }

    data class RepoConnectionStatusQuery(
        val application: EntityRef,
        val repository: EntityRef
    )
}
