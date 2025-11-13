package ru.citeck.ecos.apps.app.common

import org.springframework.stereotype.Component
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.perms.EcosPermissionsService
import ru.citeck.ecos.webapp.lib.perms.RecordPerms
import ru.citeck.ecos.webapp.lib.perms.component.artifact.SystemArtifactPermsComponent

@Component
class AppSystemArtifactPerms(
    ecosPermissionsService: EcosPermissionsService
) {

    private val permsCalc = ecosPermissionsService.createCalculator()
        .withoutDefaultComponents()
        .addComponent(SystemArtifactPermsComponent())
        .build()

    fun getPerms(ref: EntityRef): RecordPerms {
        return permsCalc.getPermissions(ref)
    }

    fun checkWrite(ref: EntityRef) {
        val perms = getPerms(ref)
        if (!perms.hasWritePerms()) {
            throw IllegalAccessException("Access denied")
        }
    }
}
