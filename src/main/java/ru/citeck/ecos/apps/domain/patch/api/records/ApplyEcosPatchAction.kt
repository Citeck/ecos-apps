package ru.citeck.ecos.apps.domain.patch.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.domain.patch.service.EcosPatchService
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.mutate.ValueMutateDao
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class ApplyEcosPatchAction(
    private val ecosPatchService: EcosPatchService
) : AbstractRecordsDao(), ValueMutateDao<ApplyEcosPatchAction.ActionDto> {

    companion object {
        const val ID = "apply-ecos-patch"
    }

    override fun getId(): String {
        return ID
    }

    override fun mutate(value: ActionDto): Any? {
        if (!AuthContext.isRunAsSystemOrAdmin()) {
            error("Permission denied")
        }
        AuthContext.runAsSystem {
            ecosPatchService.applyPatch(value.recordRef.getLocalId())
        }
        return null
    }

    class ActionDto(
        val recordRef: EntityRef
    )
}
