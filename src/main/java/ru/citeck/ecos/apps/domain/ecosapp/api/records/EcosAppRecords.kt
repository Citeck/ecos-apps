package ru.citeck.ecos.apps.domain.ecosapp.api.records

import ecos.com.fasterxml.jackson210.annotation.JsonProperty
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.domain.ecosapp.dto.EcosAppDef
import ru.citeck.ecos.apps.domain.ecosapp.service.EcosAppService
import ru.citeck.ecos.apps.domain.utils.LegacyRecordsUtils
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.records2.RecordMeta
import ru.citeck.ecos.records2.graphql.meta.value.MetaField
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.request.delete.RecordsDelResult
import ru.citeck.ecos.records2.request.delete.RecordsDeletion
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult
import ru.citeck.ecos.records2.request.query.RecordsQuery
import ru.citeck.ecos.records2.request.query.RecordsQueryResult
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDao
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao
import ru.citeck.ecos.webapp.api.entity.EntityRef
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
                ArtifactsAppQueryRes(it, appByArtifacts[it] ?: EntityRef.EMPTY)
            }
        } else {
            val predicate = recordsQuery.getQuery(Predicate::class.java)
            result.records = ecosAppService.getAll(
                predicate,
                recordsQuery.skipPage.maxItems,
                recordsQuery.skipPage.skipCount,
                LegacyRecordsUtils.mapLegacySortBy(recordsQuery.sortBy)
            ).map { EcosAppRecord(it, ecosAppService) }
            result.totalCount = ecosAppService.getCount(predicate)
        }
        return result
    }

    override fun getLocalRecordsMeta(records: List<EntityRef>, metaField: MetaField): List<Any> {
        return records.map { record ->
            ecosAppService.getById(record.getLocalId()) ?: EcosAppDef.create {}
        }.map {
            EcosAppRecord(it, ecosAppService)
        }
    }

    override fun save(values: MutableList<EcosAppRecord>): RecordsMutResult {
        val records = values.map {
            val appData = it.appData
            RecordMeta(
                EntityRef.create(
                    ID,
                    if (appData != null) {
                        ecosAppService.uploadZip(appData).id
                    } else {
                        ecosAppService.save(it.build()).id
                    }
                )
            )
        }
        val result = RecordsMutResult()
        result.records = records
        return result
    }

    override fun getValuesToMutate(records: List<EntityRef>): List<EcosAppRecord> {
        return records.map { record ->
            if (record.getLocalId().isBlank()) {
                EcosAppRecord(EcosAppDef.create {}, ecosAppService)
            } else {
                EcosAppRecord(ecosAppService.getById(record.getLocalId())!!, ecosAppService)
            }
        }
    }

    override fun delete(deletion: RecordsDeletion): RecordsDelResult {

        val result = RecordsDelResult()
        result.records = deletion.records.map {
            ecosAppService.delete(it.getLocalId())
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
