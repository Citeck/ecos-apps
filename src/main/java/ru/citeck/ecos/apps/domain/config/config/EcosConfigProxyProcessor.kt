package ru.citeck.ecos.apps.domain.config.config

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.domain.config.service.EcosConfigAppConstants
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.config.lib.dto.ConfigKey
import ru.citeck.ecos.config.lib.dto.ConfigValueDef
import ru.citeck.ecos.config.lib.dto.ConfigValueType
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.schema.SchemaAtt
import ru.citeck.ecos.records3.record.dao.impl.proxy.AttsProxyProcessor
import ru.citeck.ecos.records3.record.dao.impl.proxy.MutateProxyProcessor
import ru.citeck.ecos.records3.record.dao.impl.proxy.ProxyProcContext
import ru.citeck.ecos.records3.record.dao.impl.proxy.ProxyRecordAtts
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery

@Component
class EcosConfigProxyProcessor(
    private val recordsService: RecordsService
) : MutateProxyProcessor, AttsProxyProcessor {

    companion object {
        private const val ATT_CONFIG_ID = "configId"
        private const val ATT_CONFIG_ID_ALIAS = "___configId"
        private const val ATT_SCOPE = "scope"
        private const val ATT_SCOPE_ALIAS = "___scopeId"
    }

    override fun attsPostProcess(atts: List<ProxyRecordAtts>, context: ProxyProcContext): List<ProxyRecordAtts> {
        return atts.map {
            val configId = it.atts.getAtt(ATT_CONFIG_ID_ALIAS)[ScalarType.STR.schema].asText()
            val scope = it.atts.getAtt(ATT_SCOPE_ALIAS)[ScalarType.STR.schema].asText()
            it.copy(
                atts = it.atts.withId(it.atts.getId().withId(ConfigKey.create(scope, configId).toString()))
            )
        }
    }

    override fun attsPreProcess(schemaAtts: List<SchemaAtt>, context: ProxyProcContext): List<SchemaAtt> {
        val newAtts = schemaAtts.toMutableList()
        newAtts.add(
            SchemaAtt.create {
                withAlias(ATT_CONFIG_ID_ALIAS)
                withName(ATT_CONFIG_ID)
                withInner(SchemaAtt.create { withName(ScalarType.STR.schema) })
            }
        )
        newAtts.add(
            SchemaAtt.create {
                withAlias(ATT_SCOPE_ALIAS)
                withName(ATT_SCOPE)
                withInner(SchemaAtt.create { withName(ScalarType.STR.schema) })
            }
        )
        return newAtts
    }

    override fun mutatePostProcess(records: List<RecordRef>, context: ProxyProcContext): List<RecordRef> {
        return records
    }

    fun getConfigInnerIdByKey(key: ConfigKey): String? {
        return recordsService.queryOne(
            RecordsQuery.create {
                withSourceId(EcosConfigConfig.CONFIG_REPO_SRC_ID)
                withQuery(
                    Predicates.and(
                        Predicates.eq("scope", key.scope),
                        Predicates.eq("configId", key.id)
                    )
                )
            }
        )?.id
    }

    override fun mutatePreProcess(
        atts: List<LocalRecordAtts>,
        context: ProxyProcContext
    ): List<LocalRecordAtts> {
        return atts.map {
            var id = it.id
            if (id.contains('$')) {
                id = getConfigInnerIdByKey(ConfigKey.valueOf(id)) ?: error("Config not found by id: $id")
            } else {
                error("invalid id: $id")
            }
            LocalRecordAtts(id, preProcessBeforeMutate(id, it.attributes))
        }
    }

    private fun preProcessBeforeMutate(id: String, atts: ObjectData): ObjectData {

        val rawValue = atts["_value"]
        if (rawValue.isNull()) {
            error("_value is null")
        }

        val currentValueDto = recordsService.getAtts(
            RecordRef.create(EcosConfigConfig.CONFIG_REPO_SRC_ID, id),
            ConfigAtts::class.java
        )

        val value = convertNewValue(
            rawValue,
            currentValueDto.valueDef.type,
            currentValueDto.valueDef.multiple
        )

        val valueObj = ObjectData.create()
        valueObj[EcosConfigAppConstants.VALUE_SHORT_PROP] = value
        val newAtts = atts.deepCopy()
        newAtts["value"] = valueObj

        return newAtts
    }

    private fun convertNewValue(
        value: DataValue,
        type: ConfigValueType,
        multiple: Boolean
    ): DataValue {
        if (multiple) {
            val result = DataValue.createArr()
            val addElement: (DataValue) -> Unit = {
                if (it.isNotEmpty()) {
                    result.add(it)
                }
            }
            if (value.isArray()) {
                value.forEach {
                    addElement(convertNewValue(it, type, false))
                }
            } else {
                addElement(convertNewValue(value, type, false))
            }
            return result
        }
        val convertedValue: Any = when (type) {
            ConfigValueType.MLTEXT -> {
                if (value.isObject()) {
                    val mlTextObj = DataValue.createObj()
                    value.fieldNamesList().forEach {
                        if (!it.startsWith("_")) {
                            mlTextObj[it] = value[it]
                        }
                    }
                    mlTextObj.getAs(MLText::class.java)
                } else {
                    value.getAs(MLText::class.java)
                } ?: error("Invalid mltext: $value")
            }
            ConfigValueType.TEXT -> value.asText()
            ConfigValueType.BOOLEAN -> value.asBoolean()
            ConfigValueType.NUMBER -> value.asDouble()
            ConfigValueType.JSON -> {
                if (value.isNull() || value.isTextual() && value.asText().isBlank()) {
                    DataValue.NULL
                } else {
                    value.asObjectData()
                }
            }
            else -> error("Unknown type: $type")
        }
        return DataValue.create(convertedValue)
    }

    data class ConfigAtts(
        val configId: String,
        val scope: String,
        val value: ObjectData,
        val version: Int,
        val valueDef: ConfigValueDef
    )
}
