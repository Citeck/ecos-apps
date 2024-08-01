package ru.citeck.ecos.apps.domain.config.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.apps.domain.config.api.records.ConfigRepoMixin
import ru.citeck.ecos.apps.domain.config.service.EcosConfigAppConstants
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.config.lib.dto.ConfigKey
import ru.citeck.ecos.config.lib.dto.ConfigValue
import ru.citeck.ecos.config.lib.dto.ConfigValueDef
import ru.citeck.ecos.config.lib.zookeeper.ZkConfigProvider
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.listener.*
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.records3.record.dao.impl.proxy.*
import javax.sql.DataSource

@Configuration
class EcosConfigConfig(
    private val dbDomainFactory: DbDomainFactory,
    private val zkConfigProvider: ZkConfigProvider,
    private val recordsService: RecordsService
) {

    companion object {
        const val CONFIG_REPO_SRC_ID = "config-repo"
        const val CONFIG_SRC_ID = "config"

        private val log = KotlinLogging.logger {}
    }

    @Bean
    fun configsRepoDao(dataSource: DataSource): RecordsDao {

        val records = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(CONFIG_REPO_SRC_ID)
                        withTypeRef(ModelUtils.getTypeRef("ecos-config"))
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withTable("ecos_config")
                        withStoreTableMeta(true)
                    }
                )
                .build()
        ).withSchema("public")
            .build()

        records.addAttributesMixin(ConfigRepoMixin())

        fun updateZkValue(record: Any) {
            val atts = recordsService.getAtts(record, ChangedValueAtts::class.java)
            log.info { "Update zookeeper config value. Atts: $atts" }
            val value = atts.value[EcosConfigAppConstants.VALUE_SHORT_PROP]

            zkConfigProvider.setConfig(
                ConfigKey.create(atts.scope, atts.configId),
                ConfigValue(value, atts.valueDef)
            )
        }
        records.addListener(object : DbRecordsListenerAdapter() {
            override fun onChanged(event: DbRecordChangedEvent) {
                updateZkValue(event.record)
            }
            override fun onCreated(event: DbRecordCreatedEvent) {
                updateZkValue(event.record)
            }
        })

        return records
    }

    @Bean
    fun configsProxyDao(configProxyProc: EcosConfigProxyProcessor): RecordsDao {
        val recordsDao = object : RecordsDaoProxy(CONFIG_SRC_ID, CONFIG_REPO_SRC_ID, configProxyProc) {
            override fun getRecordsAtts(recordsId: List<String>): List<*>? {
                val ids = recordsId.map {
                    configProxyProc.getConfigInnerIdByKey(ConfigKey.valueOf(it)) ?: ""
                }
                return super.getRecordsAtts(ids)
            }

            override fun mutate(records: List<LocalRecordAtts>): List<String> {
                val result = ArrayList<String>()
                for (record in records) {
                    if (!record.hasAtt("_value")) {
                        result.add(record.id)
                    } else {
                        result.add(super.mutate(listOf(record)).first())
                    }
                }
                return result
            }
        }
        return recordsDao
    }

    data class ChangedValueAtts(
        val value: ObjectData,
        val valueDef: ConfigValueDef,
        val scope: String,
        val configId: String
    )
}
