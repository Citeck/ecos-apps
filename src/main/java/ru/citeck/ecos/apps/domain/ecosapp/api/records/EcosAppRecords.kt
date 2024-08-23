package ru.citeck.ecos.apps.domain.ecosapp.api.records

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.domain.ecosapp.dto.EcosAppDef
import ru.citeck.ecos.apps.domain.ecosapp.service.EcosAppService
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.ent.git.service.EcosVcsObjectGitService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.delete.RecordsDeleteDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDtoDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.*
import java.util.regex.Pattern

@Component
class EcosAppRecords(
    private val ecosAppService: EcosAppService,
    private val ecosVcsObjectGitService: EcosVcsObjectGitService
) : AbstractRecordsDao(),
    RecordAttsDao,
    RecordsQueryDao,
    RecordsDeleteDao,
    RecordMutateDtoDao<EcosAppRecords.EcosAppRecord> {

    companion object {
        const val ID = "ecosapp"
    }

    override fun getRecordAtts(recordId: String): Any? {
        return EcosAppRecord(
            ecosAppService.getById(recordId) ?: EcosAppDef.create {},
            ecosAppService,
            ecosVcsObjectGitService
        )
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
                    recsQuery.page.maxItems,
                    recsQuery.page.skipCount,
                    recsQuery.sortBy
                ).map { EcosAppRecord(it, ecosAppService, ecosVcsObjectGitService) }
            )
            result.setTotalCount(ecosAppService.getCount(predicate))
        }
        return result
    }

    override fun saveMutatedRec(record: EcosAppRecord): String {
        val appData = record.appData
        return if (appData != null) {
            ecosAppService.uploadZip(appData).id
        } else {
            ecosAppService.save(record.build()).id
        }
    }

    override fun getRecToMutate(recordId: String): EcosAppRecord {
        return if (recordId.isBlank()) {
            EcosAppRecord(EcosAppDef.create {}, ecosAppService, ecosVcsObjectGitService)
        } else {
            EcosAppRecord(ecosAppService.getById(recordId)!!, ecosAppService, ecosVcsObjectGitService)
        }
    }

    override fun delete(recordIds: List<String>): List<DelStatus> {
        return recordIds.map {
            ecosAppService.delete(it)
            DelStatus.OK
        }
    }

    override fun getId(): String {
        return ID
    }

    class EcosAppRecord(
        private val appDef: EcosAppDef,
        val ecosAppService: EcosAppService,
        val ecosVcsObjectGitService: EcosVcsObjectGitService
    ) : EcosAppDef.Builder(appDef) {

        var appData: ByteArray? = null

        fun setModuleId(moduleId: String) {
            withId(moduleId)
        }

        fun getData(): ByteArray {
            return ecosAppService.getAppData(appDef.id)
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

            val base64Content = content[0].get("url")
            // val filename = content[0].get("originalName", "")
            val pattern = Pattern.compile("^data:(.+?);base64,(.+)$")
            val matcher = pattern.matcher(base64Content.asText())

            check(matcher.find()) { "Incorrect content: $base64Content" }

            // val mimetype = matcher.group(1)
            val base64 = matcher.group(2)

            appData = Base64.getDecoder().decode(base64)
        }

        fun getCanVcsObjectBeCommitted(): Boolean {
            return repositoryEndpoint.isNotEmpty() && ecosVcsObjectGitService.featureAllowed()
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
