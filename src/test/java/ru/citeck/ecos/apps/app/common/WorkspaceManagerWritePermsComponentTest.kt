package ru.citeck.ecos.apps.app.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.webapp.lib.perms.RecordPermsContext

class WorkspaceManagerWritePermsComponentTest {

    private val workspaceService: WorkspaceService = mock()

    private val wsAtt = "${RecordConstants.ATT_WORKSPACE}?localId"

    private fun contextFor(workspaceLocalId: String, user: String = "alice"): RecordPermsContext {
        val ctx: RecordPermsContext = mock()
        val record: AttValueCtx = mock()
        whenever(ctx.getUser()).thenReturn(user)
        whenever(ctx.getRecord()).thenReturn(record)
        whenever(record.getAtt(wsAtt)).thenReturn(DataValue.createStr(workspaceLocalId))
        return ctx
    }

    @Test
    fun blankWorkspaceMakesComponentAbstain() {
        val component = WorkspaceManagerWritePermsComponent(workspaceService)

        assertThat(component.getRecordPerms(contextFor(""))).isNull()
        assertThat(component.getRecordAttsPerms(contextFor(""))).isNull()
    }

    @Test
    fun managerOfWorkspaceGetsWriteAndRead() {
        whenever(workspaceService.isWorkspaceWithGlobalEntities("ws1")).thenReturn(false)
        whenever(workspaceService.isUserManagerOf("alice", "ws1")).thenReturn(true)

        val component = WorkspaceManagerWritePermsComponent(workspaceService)
        val perms = component.getRecordPerms(contextFor("ws1", "alice"))

        assertThat(perms).isNotNull
        assertThat(perms!!.hasReadPerms()).isTrue
        assertThat(perms.hasWritePerms()).isTrue

        val attsPerms = component.getRecordAttsPerms(contextFor("ws1", "alice"))
        assertThat(attsPerms).isNotNull
        assertThat(attsPerms!!.hasAttReadPerms("any-attr")).isTrue
        assertThat(attsPerms.hasAttWritePerms("any-attr")).isTrue
    }

    @Test
    fun nonManagerAbstains() {
        whenever(workspaceService.isWorkspaceWithGlobalEntities("ws1")).thenReturn(false)
        whenever(workspaceService.isUserManagerOf("bob", "ws1")).thenReturn(false)

        val component = WorkspaceManagerWritePermsComponent(workspaceService)

        assertThat(component.getRecordPerms(contextFor("ws1", "bob"))).isNull()
        assertThat(component.getRecordAttsPerms(contextFor("ws1", "bob"))).isNull()
    }

    @Test
    fun globalWorkspaceMakesComponentAbstain() {
        whenever(workspaceService.isWorkspaceWithGlobalEntities("default")).thenReturn(true)

        val component = WorkspaceManagerWritePermsComponent(workspaceService)

        assertThat(component.getRecordPerms(contextFor("default"))).isNull()
        assertThat(component.getRecordAttsPerms(contextFor("default"))).isNull()
    }
}
