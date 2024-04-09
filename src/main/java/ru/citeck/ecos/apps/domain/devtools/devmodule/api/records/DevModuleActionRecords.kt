package ru.citeck.ecos.apps.domain.devtools.devmodule.api.records

import lombok.Data
import lombok.RequiredArgsConstructor
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.domain.devtools.devmodule.dto.DevModuleDef
import ru.citeck.ecos.apps.domain.devtools.devmodule.service.DevModulesService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class DevModuleActionRecords(
    val service: DevModulesService
) : AbstractRecordsDao(), RecordsQueryDao, RecordAttsDao {

    override fun getRecordAtts(recordId: String): Any {

        val actionId = recordId.substringAfterLast("$")
        val actionRef = EntityRef.create("uiserv", "action", actionId)

        val name = recordsService.getAtt(actionRef, "name").asText()

        return Record(recordId, name, actionRef)
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        return service.findAll().flatMap { toRecords(it) }
    }

    private fun toRecords(module: DevModuleDef): List<Any> {

        val names = recordsService.getAtts(module.actions, listOf("name")).map {
            it.getAtt("name").asText()
        }
        val result = mutableListOf<Any>()
        for (idx in module.actions.indices) {
            val action = module.actions[idx]
            result.add(Record(module.id + "$" + action.getLocalId(), names[idx], action))
        }
        return result
    }

    override fun getId(): String {
        return "dev-module-actions"
    }

    @Data
    @RequiredArgsConstructor
    class Record(
        val id: String,
        val name: String,
        val actionRef: EntityRef
    ) {
        @AttName("?type")
        fun getType(): EntityRef {
            return EntityRef.valueOf("emodel/type@dev-module-action")
        }
    }
}
