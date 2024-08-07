package ru.citeck.ecos.apps.domain.git.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.domain.git.service.EcosVcsObjectCommit
import ru.citeck.ecos.apps.domain.git.service.EcosVcsObjectGitService
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.value.AttValue
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDao
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef

@Component
class GitRecords(
    private val ecosVcsObjectGitService: EcosVcsObjectGitService
) : AbstractRecordsDao(), RecordAttsDao, RecordMutateDao {

    companion object {
        const val ID = "git"

        private const val ACTION_ATT = "action"
        private const val ACTION_COMMIT = "COMMIT"
    }

    override fun getId(): String {
        return ID
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

        ecosVcsObjectGitService.commitChanges(objectCommit)

        return record.id
    }

    override fun getRecordAtts(recordId: String): Any? {
        return GitRecordAtts(recordId.toEntityRef())
    }

    @Suppress("UNUSED")
    inner class GitRecordAtts(
        ecosVcsObject: EntityRef
    ) {
        var id = ecosVcsObject.toString()
        var objectRepo = RepoMeta(ecosVcsObject)
    }

    inner class RepoMeta(
        val ecosVcsObject: EntityRef
    ) : AttValue {

        private val allBranches by lazy {
            ecosVcsObjectGitService.getRepoBranchesForVcsObject(ecosVcsObject)
        }

        override fun getAtt(name: String): Any? {
            return when (name) {
                "allowedBranchesToCommit" -> ecosVcsObjectGitService.getAllowedBranchesToCommit(allBranches)
                "allowedBaseBranches" -> ecosVcsObjectGitService.getAllowedBaseBranches(allBranches)
                else -> null
            }
        }
    }
}
