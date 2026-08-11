package ru.citeck.ecos.apps.domain.patch.desc

import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef

object EcosPatchDesc {

    const val SRC_ID = "ecos-patch"

    const val ATT_DATE = "date"
    const val ATT_NAME = "name"
    const val ATT_MANUAL = "manual"
    const val ATT_TARGET_APP = "targetApp"
    const val ATT_STATE = "state"
    const val ATT_BATCH = "batch"
    const val ATT_STATUS = "status"
    const val ATT_ERRORS_COUNT = "errorsCount"
    const val ATT_PATCH_ID = "patchId"
    const val ATT_LAST_ERROR = "lastError"
    const val ATT_DEPENDS_ON = "dependsOn"
    const val ATT_NEXT_EXEC_DATE = "nextExecDate"
    const val ATT_DEPENDS_ON_APPS = "dependsOnApps"

    // Keys inside a patch's `state`. For a batched patch the top-level state is orchestration state
    // (next batch offset), so the executor's own state is nested under STATE_COMMAND_STATE instead
    // of replacing the whole state as the non-batched path does.
    const val STATE_BATCH_OFFSET = "batchOffset"
    const val STATE_COMMAND_STATE = "commandState"

    fun getRef(id: String): EntityRef {
        return EntityRef.create(AppName.EAPPS, SRC_ID, id)
    }
}
