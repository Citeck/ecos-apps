package ru.citeck.ecos.apps.domain.ecosapp.api.records

import ecos.com.fasterxml.jackson210.annotation.JsonProperty
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.domain.ecosapp.dto.EcosAppDef
import ru.citeck.ecos.apps.domain.ecosapp.service.EcosAppService
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.records2.QueryContext
import ru.citeck.ecos.records2.RecordMeta
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt
import ru.citeck.ecos.records2.graphql.meta.value.MetaField
import ru.citeck.ecos.records2.request.delete.RecordsDelResult
import ru.citeck.ecos.records2.request.delete.RecordsDeletion
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult
import ru.citeck.ecos.records2.request.query.RecordsQuery
import ru.citeck.ecos.records2.request.query.RecordsQueryResult
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDao
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao
import java.util.*
import java.util.regex.Pattern

@Component
class EcosAppRecords(
    private val ecosAppService: EcosAppService
) : LocalRecordsDao(),
    LocalRecordsMetaDao<Any>,
    LocalRecordsQueryWithMetaDao<Any>,
    MutableRecordsLocalDao<EcosAppRecords.EcosAppRecord> {

    companion object {
        const val ID = "ecosapp"
    }

    init {
        id = ID
    }

    override fun queryLocalRecords(recordsQuery: RecordsQuery, field: MetaField): RecordsQueryResult<Any> {

        val result = RecordsQueryResult<Any>()

        if (recordsQuery.language == "artifacts-app") {

            val query = recordsQuery.getQuery(ArtifactsAppQuery::class.java)
            val appByArtifacts = ecosAppService.getAppForArtifacts(query.artifacts)

            result.records = query.artifacts.map {
                ArtifactsAppQueryRes(it, appByArtifacts[it] ?: RecordRef.EMPTY)
            }

        } else {

            result.records = ecosAppService.getAll().map { EcosAppRecord(it, ecosAppService) }
        }
        return result
    }

    override fun getLocalRecordsMeta(records: List<RecordRef>, metaField: MetaField): List<Any> {
        return records.map { record ->
            ecosAppService.getById(record.id) ?: EcosAppDef.create {}
        }.map {
            EcosAppRecord(it, ecosAppService)
        }
    }

    override fun save(values: MutableList<EcosAppRecord>): RecordsMutResult {
        val records = values.map {
            val appData = it.appData
            RecordMeta(RecordRef.create(ID, if (appData != null) {
                ecosAppService.uploadZip(appData).id
            } else {
                ecosAppService.save(it.build()).id
            }))
        }
        val result = RecordsMutResult()
        result.records = records
        return result
    }

    override fun getValuesToMutate(records: List<RecordRef>): List<EcosAppRecord> {
        return records.map { record ->
            if (record.id.isBlank()) {
                EcosAppRecord(EcosAppDef.create {}, ecosAppService)
            } else {
                EcosAppRecord(ecosAppService.getById(record.id)!!, ecosAppService)
            }
        }
    }

    override fun delete(deletion: RecordsDeletion): RecordsDelResult {

        val result = RecordsDelResult()
        result.records = deletion.records.map {
            ecosAppService.delete(it.id)
            it
        }.map { RecordMeta(it) }

        return result
    }

    class EcosAppRecord(private val appDef: EcosAppDef, val ecosAppService: EcosAppService) : EcosAppDef.Builder(appDef) {

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

        @MetaAtt(".type")
        fun getEcosType(): RecordRef {
            return RecordRef.valueOf("emodel/type@ecos-app")
        }

        @MetaAtt(".disp")
        fun getDisplayName(): String {
            return MLText.getClosestValue(name, QueryContext.getCurrent<QueryContext>().locale)
        }

        @JsonProperty("_content")
        fun setContent(content: List<ObjectData>) {

            val base64Content = content[0].get("url")
            //val filename = content[0].get("originalName", "")
            val pattern = Pattern.compile("^data:(.+?);base64,(.+)$")
            val matcher = pattern.matcher(base64Content.asText())

            check(matcher.find()) { "Incorrect content: $base64Content" }

            //val mimetype = matcher.group(1)
            val base64 = matcher.group(2)

            appData = Base64.getDecoder().decode(base64)
        }
    }

    data class TypeArtifactsQuery(
        val typeRefs: List<RecordRef>
    )

    data class ArtifactsAppQuery(
        val artifacts: List<RecordRef>
    )

    data class ArtifactsAppQueryRes(
        val artifact: RecordRef,
        val ecosApp: RecordRef
    )
}
