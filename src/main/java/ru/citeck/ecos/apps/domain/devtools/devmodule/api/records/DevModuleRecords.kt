package ru.citeck.ecos.apps.domain.devtools.devmodule.api.records

import lombok.Data
import lombok.RequiredArgsConstructor
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.domain.devtools.devmodule.dto.DevModuleDef
import ru.citeck.ecos.apps.domain.devtools.devmodule.service.DevModulesService
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery

@Component
class DevModuleRecords(
    val service: DevModulesService
) : AbstractRecordsDao(), RecordsQueryDao, RecordAttsDao {

    override fun getRecordAtts(recordId: String): Any? {
        return service.getById(recordId)?.let { Record(it, emptyList()) }
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        return service.findAll().map { Record(it, emptyList()) }
    }

    override fun getId(): String {
        return "dev-module"
    }

    @Data
    @RequiredArgsConstructor
    class Record(
        @AttName("...")
        val def: DevModuleDef,
        val customActions: List<Any>
    ) {
        @AttName("?type")
        fun getType(): RecordRef {
            return RecordRef.valueOf("emodel/type@dev-module")
        }

        fun getActions(): List<Any> {
            val result = arrayListOf<Any>()
            result.addAll(def.actions)
            result.addAll(customActions)
            return result
        }
    }
}
