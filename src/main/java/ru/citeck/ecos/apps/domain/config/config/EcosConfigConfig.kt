package ru.citeck.ecos.apps.domain.config.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.apps.domain.config.api.records.ConfigValueMixin
import ru.citeck.ecos.apps.domain.config.service.EcosConfigAppConstants
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.config.lib.dto.ConfigKey
import ru.citeck.ecos.config.lib.dto.ConfigValue
import ru.citeck.ecos.config.lib.dto.ConfigValueDef
import ru.citeck.ecos.config.lib.zookeeper.ZkConfigService
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.dto.DbTableRef
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.lib.attributes.dto.AttributeType
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.records3.record.dao.impl.proxy.MutateProxyProcessor
import ru.citeck.ecos.records3.record.dao.impl.proxy.ProxyProcContext
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import ru.citeck.ecos.records3.record.request.RequestContext
import javax.sql.DataSource

@Configuration
class EcosConfigConfig(
    private val dbDomainFactory: DbDomainFactory,
    private val recordsService: RecordsService
) {

    companion object {
        const val CONFIG_REPO_SRC_ID = "config-repo"
        const val CONFIG_SRC_ID = "config"
    }

    @Bean
    fun configsRepoDao(dataSource: DataSource): RecordsDao {

        val records = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(CONFIG_REPO_SRC_ID)
                        withTypeRef(TypeUtils.getTypeRef("ecos-config"))
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withAuthEnabled(false)
                        withTableRef(DbTableRef("public", "ecos_config"))
                        withTransactional(false)
                        withStoreTableMeta(true)
                    }
                )
                .build()
        ).build()

        records.addAttributesMixin(ConfigValueMixin())

        return records
    }

    @Bean
    fun configsProxyDao(zkConfigService: ZkConfigService): RecordsDao {

        val proxyDao = RecordsDaoProxy(
            CONFIG_SRC_ID, CONFIG_REPO_SRC_ID,
            object : MutateProxyProcessor {

                override fun mutatePostProcess(records: List<RecordRef>, context: ProxyProcContext): List<RecordRef> {
                    return records
                }

                override fun mutatePreProcess(
                    atts: List<LocalRecordAtts>,
                    context: ProxyProcContext
                ): List<LocalRecordAtts> {
                    return atts.map { LocalRecordAtts(it.id, preProcess(it.id, it.attributes)) }
                }

                private fun preProcess(id: String, atts: ObjectData): ObjectData {

                    val rawValue = atts["_value"]
                    if (rawValue.isNull()) {
                        error("_value is null")
                    }

                    val currentValueDto = recordsService.getAtts("config-repo@$id", ConfigAtts::class.java)
                    val currentValue = currentValueDto.value[EcosConfigAppConstants.VALUE_SHORT_PROP]
                    if (currentValue.isNull()) {
                        error("currentValue is null. Obj: $currentValueDto")
                    }

                    val type = currentValueDto.valueDef.type
                    val value = DataValue.create(if (type == AttributeType.MLTEXT) {
                        if (rawValue.isObject()) {
                            val mlTextObj = DataValue.createObj()
                            rawValue.fieldNamesList().forEach {
                                if (!it.startsWith("_")) {
                                    mlTextObj[it] = rawValue[it]
                                }
                            }
                            mlTextObj.getAs(MLText::class.java)
                        } else {
                            rawValue.getAs(MLText::class.java)
                        } ?: error("Invalid mltext: $rawValue")
                    } else {
                        if (currentValue.isBoolean()) {
                            rawValue.asBoolean()
                        } else if (currentValue.isIntegralNumber()) {
                            rawValue.asLong()
                        } else if (currentValue.isFloatingPointNumber()) {
                            rawValue.asDouble()
                        } else if (currentValue.isTextual()) {
                            rawValue.asText()
                        } else if (currentValue.isObject()) {
                            rawValue.asObjectData()
                        } else {
                            error("Current value is not modifiable: $currentValue")
                        }
                    })

                    RequestContext.doAfterCommit {
                        val configValue = ConfigValue(
                            value,
                            currentValueDto.valueDef
                        )
                        zkConfigService.setConfig(
                            ConfigKey.create(currentValueDto.scope, currentValueDto.configId),
                            configValue
                        )
                    }

                    val valueObj = ObjectData.create()
                    valueObj[EcosConfigAppConstants.VALUE_SHORT_PROP] = value
                    val newAtts = atts.deepCopy()
                    newAtts["value"] = valueObj

                    return newAtts
                }
            }
        )

        return proxyDao
    }

    data class ConfigAtts(
        val configId: String,
        val scope: String,
        val value: ObjectData,
        val version: Int,
        val valueDef: ConfigValueDef
    )
}
