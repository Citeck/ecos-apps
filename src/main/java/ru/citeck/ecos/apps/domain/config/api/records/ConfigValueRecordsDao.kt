package ru.citeck.ecos.apps.domain.config.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.domain.config.config.EcosConfigConfig
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.config.lib.dto.ConfigKey
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsServiceFactory
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.atts.value.AttValue
import ru.citeck.ecos.records3.record.atts.value.AttValuesConverter
import ru.citeck.ecos.records3.record.atts.value.impl.AttValueDelegate
import ru.citeck.ecos.records3.record.atts.value.impl.NullAttValue
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery

@Component
class ConfigValueRecordsDao : AbstractRecordsDao(), RecordAttsDao, RecordMutateDao {

    companion object {
        const val ID = "config-value"
    }

    private lateinit var attValuesConverter: AttValuesConverter

    override fun getRecordAtts(recordId: String): Any? {
        val key = ConfigKey.valueOf(recordId)
        val atts = getRecAtts(key)
        val attValue = attValuesConverter.toAttValue(atts.value) ?: NullAttValue.INSTANCE
        val displayName = (atts.name ?: "").ifBlank { key.id }
        return ConfigValueRecord(attValue, key, atts.id, displayName, atts.formRef)
    }

    override fun mutate(record: LocalRecordAtts): String {
        val configId = getRecAtts(ConfigKey.valueOf(record.id)).id
        if (configId.isEmpty()) {
            error("Config is not found: " + record.id)
        }
        val ref = RecordRef.create(EcosConfigConfig.CONFIG_SRC_ID, configId)
        if (record.attributes.has(RecordConstants.ATT_SELF)) {
            recordsService.mutateAtt(ref, "_value", record.attributes[RecordConstants.ATT_SELF])
        } else {
            val atts = record.attributes.deepCopy()
            atts.fieldNamesList().forEach {
                if (it.startsWith('_')) {
                    atts.remove(it)
                }
            }
            recordsService.mutateAtt(ref,"_value", atts)
        }
        return record.id
    }

    private fun getRecAtts(key: ConfigKey): AttsFromConfigDef {
        return recordsService.queryOne(RecordsQuery.create {
            withSourceId(EcosConfigConfig.CONFIG_SRC_ID)
            withQuery(Predicates.and(
                Predicates.eq("scope", key.scope),
                Predicates.eq("configId", key.id)
            ))
        }, AttsFromConfigDef::class.java) ?: error("Config is not found: $key")
    }

    override fun getId(): String {
        return ID
    }

    override fun setRecordsServiceFactory(serviceFactory: RecordsServiceFactory) {
        super.setRecordsServiceFactory(serviceFactory)
        this.attValuesConverter = serviceFactory.attValuesConverter
    }

    private class ConfigValueRecord(
        value: AttValue,
        val key: ConfigKey,
        val defId: String,
        val displayName: String,
        val formRef: RecordRef
    ) : AttValueDelegate(value) {
        override fun getAtt(name: String): Any? {
            return when (name) {
                "defRef" -> RecordRef.create(EcosConfigConfig.CONFIG_SRC_ID, defId)
                RecordConstants.ATT_FORM_REF -> if (RecordRef.isNotEmpty(formRef)) {
                    formRef
                } else {
                    RecordRef.create("uiserv", "form", "config\$$key")
                }
                else -> super.getAtt(name)
            }
        }
        override fun getDisplayName(): Any {
            return displayName
        }
    }

    private class AttsFromConfigDef(
        val id: String,
        @AttName("_value")
        val value: DataValue?,
        @AttName("name")
        val name: String?,
        @AttName("valueDef.formRef?id!''")
        val formRef: RecordRef
    )
}
