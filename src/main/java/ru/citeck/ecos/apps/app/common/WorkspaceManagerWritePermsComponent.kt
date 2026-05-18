package ru.citeck.ecos.apps.app.common

import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.webapp.lib.perms.RecordPermsContext
import ru.citeck.ecos.webapp.lib.perms.component.RecordAttsPermsComponent
import ru.citeck.ecos.webapp.lib.perms.component.RecordAttsPermsData
import ru.citeck.ecos.webapp.lib.perms.component.RecordPermsComponent
import ru.citeck.ecos.webapp.lib.perms.component.RecordPermsData

/**
 * Grants write to a workspace manager for records that expose the
 * `_workspace` attribute (an EntityRef of the workspace; `?localId`
 * gives the workspace id string). Reads workspace from the record's
 * attribute rather than parsing the ref id, so we don't depend on the
 * wsSysId↔workspaceId round-trip working at the time of the check.
 *
 * Returns `null` for non-applicable cases (global workspace, or caller is not
 * a manager of the record's workspace) so other components in the calculator
 * (notably `SystemArtifactPermsComponent`) still supply read/write for
 * ADMIN/SYSTEM.
 */
class WorkspaceManagerWritePermsComponent(
    private val workspaceService: WorkspaceService
) : RecordPermsComponent,
    RecordAttsPermsComponent {

    companion object {
        private const val WORKSPACE_LOCAL_ID_ATT = "${RecordConstants.ATT_WORKSPACE}?localId"
    }

    override fun getRecordPerms(context: RecordPermsContext): RecordPermsData? {
        return if (isManagerOfRecordWorkspace(context)) WsManagerPerms else null
    }

    override fun getRecordAttsPerms(context: RecordPermsContext): RecordAttsPermsData? {
        return if (isManagerOfRecordWorkspace(context)) WsManagerPerms else null
    }

    private fun isManagerOfRecordWorkspace(context: RecordPermsContext): Boolean {
        val workspace = context.getRecord().getAtt(WORKSPACE_LOCAL_ID_ATT).asText()
        if (workspace.isBlank() || workspaceService.isWorkspaceWithGlobalEntities(workspace)) {
            return false
        }
        return workspaceService.isUserManagerOf(context.getUser(), workspace)
    }

    private object WsManagerPerms : RecordPermsData, RecordAttsPermsData {
        override fun hasReadPerms(): Boolean = true
        override fun hasWritePerms(): Boolean = true
        override fun hasAttReadPerms(name: String): Boolean = true
        override fun hasAttWritePerms(name: String): Boolean = true
        override fun getAdditionalPerms(): Set<String> = emptySet()
    }
}
