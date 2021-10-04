package ru.citeck.ecos.apps.domain.config.config

import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.apps.domain.config.api.records.ConfigFormMixin
import ru.citeck.ecos.apps.domain.config.api.records.ConfigValueMixin
import ru.citeck.ecos.apps.domain.config.service.EcosConfigAppConstants
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.config.lib.dto.ConfigKey
import ru.citeck.ecos.config.lib.zookeeper.ZkConfigService
import ru.citeck.ecos.data.sql.datasource.DbDataSourceImpl
import ru.citeck.ecos.data.sql.dto.DbTableRef
import ru.citeck.ecos.data.sql.pg.PgDataServiceFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDao
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.perms.DefaultDbPermsComponent
import ru.citeck.ecos.data.sql.repo.entity.DbEntity
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.data.sql.service.DbDataServiceImpl
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.type.dto.TypeInfo
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.type.repo.TypesRepo
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
class EcosConfigConfiguration(
    private val applicationContext: ApplicationContext,
    private val recordsService: RecordsService
) {

    private val attributes: List<AttributeDef> by lazy { getAttributesImpl() }

    @Bean
    fun configsRepoDao(dataSource: DataSource): RecordsDao {

        val pgDataServiceFactory = PgDataServiceFactory()
        val dbDataSource = DbDataSourceImpl(dataSource)
        val dbDataService = DbDataServiceImpl(
            DbEntity::class.java,
            DbDataServiceConfig.create {
                withAuthEnabled(false)
                withTableRef(DbTableRef("public", "ecos_config"))
                withTransactional(false)
                withStoreTableMeta(true)
                withMaxItemsToAllowSchemaMigration(1000)
            },
            dbDataSource,
            pgDataServiceFactory
        )

        val records = DbRecordsDao(
            "config-repo",
            DbRecordsDaoConfig(
                TypeUtils.getTypeRef("ecos-config"),
                insertable = true,
                updatable = true,
                deletable = true
            ),
            object : TypesRepo {
                override fun getChildren(typeRef: RecordRef): List<RecordRef> {
                    return emptyList()
                }
                override fun getTypeInfo(typeRef: RecordRef): TypeInfo? {
                    val typeId = typeRef.id
                    if (typeId != "ecos-config") {
                        return null
                    }
                    return TypeInfo.create {
                        withId(typeId)
                        withModel(
                            TypeModelDef.create()
                                .withAttributes(attributes)
                                .build()
                        )
                    }
                }
            },
            dbDataService,
            DefaultDbPermsComponent(recordsService),
            null
        )

        records.addAttributesMixin(ConfigFormMixin())
        records.addAttributesMixin(ConfigValueMixin())

        return records
    }

    @Bean
    fun configsProxyDao(zkConfigService: ZkConfigService): RecordsDao {

        val proxyDao = RecordsDaoProxy("config", "config-repo", object : MutateProxyProcessor {

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

                val rawValue = atts.get("_value")
                if (rawValue.isNull()) {
                    error("_value is null")
                }

                val currentValueDto = recordsService.getAtts("config-repo@$id", ConfigAtts::class.java)
                val currentValue = currentValueDto.value.get(EcosConfigAppConstants.VALUE_SHORT_PROP)
                if (currentValue.isNull()) {
                    error("currentValue is null. Obj: $currentValueDto")
                }

                val value = DataValue.create(if (currentValue.isBoolean()) {
                    rawValue.asBoolean()
                } else if (currentValue.isIntegralNumber()) {
                    rawValue.asLong()
                } else if (currentValue.isFloatingPointNumber()) {
                    rawValue.asDouble()
                } else if (currentValue.isTextual()) {
                    rawValue.asText()
                } else {
                    error("Current value is not modifiable: $currentValue")
                })

                RequestContext.doAfterCommit {
                    zkConfigService.setConfig(
                        ConfigKey(currentValueDto.configId, currentValueDto.scope),
                        value,
                        currentValueDto.version
                    )
                }

                val valueObj = ObjectData.create()
                valueObj.set(EcosConfigAppConstants.VALUE_SHORT_PROP, value)
                val newAtts = atts.deepCopy()
                newAtts.set("value", valueObj)

                return newAtts
            }
        })

        return proxyDao
    }

    private fun getAttributesImpl(): List<AttributeDef> {
        val configFile = applicationContext.getResource("classpath:eapps/artifacts/model/type/ecos-config.yml")
        val typeDef = Json.mapper.read(configFile.file, TypeDef::class.java) ?: error("type config reading error")
        return typeDef.model.attributes
    }

    data class ConfigAtts(
        val configId: String,
        val scope: String,
        val value: ObjectData,
        val version: Int
    )

    class TypeDef(
        val model: TypeModelDef
    )
}
