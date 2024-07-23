package ru.citeck.ecos.apps.domain.git.api.records

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.domain.git.service.EcosObjectCommit
import ru.citeck.ecos.apps.domain.git.service.EcosObjectGitService
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
    private val ecosObjectGitService: EcosObjectGitService
) : AbstractRecordsDao(), RecordAttsDao, RecordMutateDao {

    companion object {
        const val ID = "git"

        private val log = KotlinLogging.logger {}

        private const val ACTION_ATT = "action"
        private const val ACTION_COMMIT = "COMMIT"
    }

    override fun getId(): String {
        return ID
    }

    override fun mutate(record: LocalRecordAtts): String {
        // TODO: develop a more sophisticated check bases on the user's roles
        if (AuthContext.isRunAsSystemOrAdmin().not()) {
            error("Only system or admin can use git integration")
        }

        val action = record.getAtt(ACTION_ATT).asText()
        if (action != ACTION_COMMIT) {
            error("Unsupported mutate action: $action")
        }

        val isNewBranch = record.getAtt("newBranch").asBoolean()
        val branchName = if (isNewBranch) {
            record.getAtt("newBranchName").asText()
        } else {
            record.getAtt("branch").asText()
        }

        val objectCommit = EcosObjectCommit(
            objectRef = record.id.toEntityRef(),
            branch = branchName,
            commitMessage = record.getAtt("commitMessage").asText(),
            newBranch = isNewBranch,
            newBranchFrom = record.getAtt("newBranchFrom").asText()
        )

        log.error { "mutate: $record" }

        ecosObjectGitService.commitChanges(objectCommit)

        return record.id
    }

    override fun getRecordAtts(recordId: String): Any? {
        return GitRecordAtts(recordId.toEntityRef())
    }

    inner class GitRecordAtts(
        ecosObject: EntityRef
    ) {

        var id = ecosObject.toString()

        var objectRepo = RepoMeta(ecosObject)

    }

    inner class RepoMeta(
        val ecosObject: EntityRef
    ) : AttValue {

        override fun getAtt(name: String): Any? {

            if (name == "branches") {
                return ecosObjectGitService.getRepoBranches(ecosObject)
            }

            return super.getAtt(name)
        }
    }
}
