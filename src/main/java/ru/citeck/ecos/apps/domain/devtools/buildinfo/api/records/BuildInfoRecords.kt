package ru.citeck.ecos.apps.domain.devtools.buildinfo.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.buildinfo.dto.BuildInfo
import ru.citeck.ecos.apps.app.service.remote.RemoteAppStatus
import ru.citeck.ecos.records3.record.atts.value.impl.EmptyAttValue
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordsAttsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import java.util.concurrent.ConcurrentHashMap

@Component
class BuildInfoRecords : AbstractRecordsDao(), RecordsQueryDao, RecordsAttsDao {

    companion object {
        const val ID = "build-info"
    }

    private val fullBuildInfo: MutableMap<String, Record> = ConcurrentHashMap()

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        return fullBuildInfo.values.toList()
    }

    override fun getRecordsAtts(recordsId: List<String>): List<*>? {
        return recordsId.map { fullBuildInfo[it] ?: EmptyAttValue.INSTANCE }
    }

    fun register(app: RemoteAppStatus, buildInfo: List<BuildInfo>) {
        for (info in buildInfo) {
            val id: String = app.appName + "-" + info.repo
            val currentInfo = fullBuildInfo[id]
            if (currentInfo == null || currentInfo.info.buildDate.isBefore(info.buildDate)) {
                fullBuildInfo[id] = Record(id, app, info)
            }
        }
    }

    class Record(
        val id: String,
        val app: RemoteAppStatus,
        val info: BuildInfo
    ) {
        fun getLabel(): String {
            var label = info.repo
            val slashIdx = label.indexOf('/')
            if (slashIdx != -1 && slashIdx < label.length - 1) {
                label = label.substring(slashIdx + 1)
            }
            if (label.endsWith(".git")) {
                label = label.substring(0, label.length - 4)
            }
            return label
        }
    }

    override fun getId() = ID
}
