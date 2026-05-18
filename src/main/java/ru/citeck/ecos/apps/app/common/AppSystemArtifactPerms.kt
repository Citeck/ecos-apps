package ru.citeck.ecos.apps.app.common

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.perms.EcosPermissionsService
import ru.citeck.ecos.webapp.lib.perms.RecordPerms
import ru.citeck.ecos.webapp.lib.perms.component.artifact.SystemArtifactPermsComponent

@Component
class AppSystemArtifactPerms(
    ecosPermissionsService: EcosPermissionsService,
    private val workspaceService: WorkspaceService
) {

    private val permsCalc = ecosPermissionsService.createCalculator()
        .withoutDefaultComponents()
        .addComponent(WorkspaceManagerWritePermsComponent(workspaceService))
        .addComponent(SystemArtifactPermsComponent())
        .build()

    fun getPerms(ref: EntityRef): RecordPerms {
        return permsCalc.getPermissions(ref)
    }

    fun checkWrite(ref: EntityRef) {
        val perms = getPerms(ref)
        if (!perms.hasWritePerms()) {
            throw SecurityException("Access denied")
        }
    }

    /**
     * Imperative write check for service-initiated mutations, where the caller
     * already knows the target workspace. Bypasses both the calculator and
     * `workspaceService.addWsPrefixToId` — important during early bootstrap when
     * wsSysId resolution may not be ready, and unnecessary for SYSTEM/ADMIN anyway.
     */
    fun checkWrite(appName: String, type: String, id: String, workspace: String) {
        if (AuthContext.isRunAsSystemOrAdmin()) {
            return
        }
        if (workspace.isNotBlank() &&
            !workspaceService.isWorkspaceWithGlobalEntities(workspace) &&
            workspaceService.isUserManagerOf(AuthContext.getCurrentRunAsUser(), workspace)
        ) {
            return
        }
        throw SecurityException("Access denied")
    }
}
