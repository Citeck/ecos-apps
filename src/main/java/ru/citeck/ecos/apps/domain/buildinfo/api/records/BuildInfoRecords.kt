package ru.citeck.ecos.apps.domain.buildinfo.api.records

import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao
import java.util.concurrent.ConcurrentHashMap
import ru.citeck.ecos.records2.request.query.RecordsQuery
import ru.citeck.ecos.records2.graphql.meta.value.MetaField
import ru.citeck.ecos.records2.request.query.RecordsQueryResult
import java.util.stream.Collectors
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.graphql.meta.value.EmptyValue
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.buildinfo.dto.BuildInfo
import ru.citeck.ecos.apps.app.remote.AppStatus
import java.util.*

@Component
class BuildInfoRecords : LocalRecordsDao(), LocalRecordsQueryWithMetaDao<Any>, LocalRecordsMetaDao<Any> {

    companion object {
        const val ID = "build-info"
    }

    private val fullBuildInfo: MutableMap<String, Record> = ConcurrentHashMap()

    override fun queryLocalRecords(recordsQuery: RecordsQuery, field: MetaField): RecordsQueryResult<Any> {
        return RecordsQueryResult(ArrayList<Any>(fullBuildInfo.values))
    }

    override fun getLocalRecordsMeta(records: List<RecordRef>, metaField: MetaField): List<Any> {
        return records.stream().map { ref: RecordRef ->
            fullBuildInfo[ref.id] ?: EmptyValue.INSTANCE
        }.collect(Collectors.toList())
    }

    override fun getId(): String {
        return ID
    }

    fun register(app: AppStatus, buildInfo: List<BuildInfo>) {
        for (info in buildInfo) {
            val id = app.appName + "-" + info.repo
            val currentInfo = fullBuildInfo[id]
            if (currentInfo == null || currentInfo.info.buildDate.isBefore(info.buildDate)) {
                fullBuildInfo[id] = Record(id, app, info)
            }
        }
    }

    class Record(
        val id: String,
        val app: AppStatus,
        val info: BuildInfo
    )
}
