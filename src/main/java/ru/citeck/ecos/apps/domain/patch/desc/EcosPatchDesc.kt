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
    const val ATT_STATUS = "status"
    const val ATT_ERRORS_COUNT = "errorsCount"
    const val ATT_PATCH_ID = "patchId"
    const val ATT_DEPENDS_ON = "dependsOn"
    const val ATT_NEXT_EXEC_DATE = "nextExecDate"
    const val ATT_DEPENDS_ON_APPS = "dependsOnApps"

    fun getRef(id: String): EntityRef {
        return EntityRef.create(AppName.EAPPS, SRC_ID, id)
    }
}
