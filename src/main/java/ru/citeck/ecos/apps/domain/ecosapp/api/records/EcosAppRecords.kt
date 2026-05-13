package ru.citeck.ecos.apps.domain.ecosapp.api.records

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.common.AppSystemArtifactPerms
import ru.citeck.ecos.apps.domain.ecosapp.dto.EcosAppDef
import ru.citeck.ecos.apps.domain.ecosapp.service.EcosAppService
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.ent.git.service.EcosVcsObjectGitService
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.atts.value.impl.EmptyAttValue
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.delete.RecordsDeleteDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDtoDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.authority.EcosAuthoritiesApi
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.perms.RecordPerms
import java.time.Instant
import java.util.*
import java.util.regex.Pattern

@Component
class EcosAppRecords(
    private val ecosAppService: EcosAppService,
    private val ecosVcsObjectGitService: EcosVcsObjectGitService,
    private val perms: AppSystemArtifactPerms,
    private val workspaceService: WorkspaceService,
    private val authorities: EcosAuthoritiesApi
) : AbstractRecordsDao(),
    RecordAttsDao,
    RecordsQueryDao,
    RecordsDeleteDao,
    RecordMutateDtoDao<EcosAppRecords.EcosAppRecord> {

    companion object {
        const val ID = "ecosapp"
    }

    override fun getRecordAtts(recordId: String): Any? {
        val idInWs = workspaceService.convertToIdInWs(recordId)
        val appDef = ecosAppService.getByIdWithMeta(idInWs.id, idInWs.workspace) ?: return EmptyAttValue.INSTANCE
        return EcosAppRecord(appDef)
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        val result = RecsQueryRes<Any>()

        if (recsQuery.language == "artifacts-app") {

            val query = recsQuery.getQuery(ArtifactsAppQuery::class.java)
            val appByArtifacts = ecosAppService.getAppForArtifacts(query.artifacts)

            result.setRecords(
                query.artifacts.map {
                    ArtifactsAppQueryRes(it, appByArtifacts[it] ?: EntityRef.EMPTY)
                }
            )
        } else {
            val predicate = recsQuery.getQuery(Predicate::class.java)
            result.setRecords(
                ecosAppService.getAll(
                    predicate,
                    recsQuery.workspaces,
                    recsQuery.page.maxItems,
                    recsQuery.page.skipCount,
                    recsQuery.sortBy
                ).map { EcosAppRecord(it) }
            )
            result.setTotalCount(ecosAppService.getCount(predicate, recsQuery.workspaces))
        }
        return result
    }

    override fun saveMutatedRec(record: EcosAppRecord): String {
        val appData = record.appData
        val savedDef = if (appData != null) {
            ecosAppService.uploadZip(appData, record.workspace)
        } else {
            ecosAppService.save(record.build())
        }
        return workspaceService.addWsPrefixToId(savedDef.id, savedDef.workspace)
    }

    override fun getRecToMutate(recordId: String): EcosAppRecord {
        if (recordId.isBlank()) {
            return EcosAppRecord(EntityWithMeta(EcosAppDef.create {}))
        }
        val idInWs = workspaceService.convertToIdInWs(recordId)
        val appDef = ecosAppService.getByIdWithMeta(idInWs.id, idInWs.workspace)
            ?: error("ECOS application not found: $recordId")
        return EcosAppRecord(appDef)
    }

    override fun delete(recordIds: List<String>): List<DelStatus> {
        return recordIds.map {
            val idInWs = workspaceService.convertToIdInWs(it)
            ecosAppService.delete(idInWs.id, idInWs.workspace)
            DelStatus.OK
        }
    }

    override fun getId(): String {
        return ID
    }

    inner class EcosAppRecord(
        appDefEntity: EntityWithMeta<EcosAppDef>
    ) : EcosAppDef.Builder(appDefEntity.entity) {

        private val appDef = appDefEntity.entity
        private val meta = appDefEntity.meta

        var appData: ByteArray? = null

        fun setModuleId(moduleId: String) {
            val idInWs = workspaceService.convertToIdInWs(moduleId)
            withId(idInWs.id)
            if (idInWs.workspace.isNotBlank()) {
                withWorkspace(idInWs.workspace)
            }
        }

        @JsonProperty(RecordConstants.ATT_WORKSPACE)
        fun setCtxWorkspace(workspace: String?) {
            withWorkspace(workspaceService.getUpdatedWsInMutation(this.workspace, workspace))
        }

        @AttName("?id")
        fun getRecordId(): String {
            return workspaceService.addWsPrefixToId(appDef.id, appDef.workspace)
        }

        fun getData(): ByteArray {
            return ecosAppService.getAppData(appDef.id, appDef.workspace)
        }

        fun getModuleId(): String {
            return appDef.id
        }

        fun getEcosType(): EntityRef {
            return EntityRef.valueOf("emodel/type@ecos-app")
        }

        fun getDisplayName(): String {
            return MLText.getClosestValue(name, I18nContext.getLocale())
        }

        @JsonProperty("_content")
        fun setContent(content: List<ObjectData>) {

            val base64Content = content[0]["url"]
            val pattern = Pattern.compile("^data:(.+?);base64,(.+)$")
            val matcher = pattern.matcher(base64Content.asText())

            check(matcher.find()) { "Incorrect content: $base64Content" }

            val base64 = matcher.group(2)

            appData = Base64.getDecoder().decode(base64)
        }

        fun getCanVcsObjectBeCommitted(): Boolean {
            return repositoryEndpoint.isNotEmpty() && ecosVcsObjectGitService.featureAllowed()
        }

        fun getPermissions(): RecordPerms {
            val fullId = workspaceService.addWsPrefixToId(appDef.id, appDef.workspace)
            return perms.getPerms(EntityRef.create(AppName.EAPPS, ID, fullId))
        }

        @AttName(RecordConstants.ATT_CREATOR)
        fun getCreator(): EntityRef {
            return authorities.getPersonRef(meta.creator)
        }

        @AttName(RecordConstants.ATT_CREATED)
        fun getCreated(): Instant {
            return meta.created
        }

        @AttName(RecordConstants.ATT_MODIFIED)
        fun getModified(): Instant {
            return meta.modified
        }

        @AttName(RecordConstants.ATT_MODIFIER)
        fun getModifier(): EntityRef {
            return authorities.getPersonRef(meta.modifier)
        }
    }

    data class TypeArtifactsQuery(
        val typeRefs: List<EntityRef>
    )

    data class ArtifactsAppQuery(
        val artifacts: List<EntityRef>
    )

    data class ArtifactsAppQueryRes(
        val artifact: EntityRef,
        val ecosApp: EntityRef
    )
}
