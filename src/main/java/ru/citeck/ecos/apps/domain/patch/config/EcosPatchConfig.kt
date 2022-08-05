package ru.citeck.ecos.apps.domain.patch.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.apps.domain.patch.service.EcosPatchProperties
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.dto.DbTableRef
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment
import javax.sql.DataSource

@Configuration
class EcosPatchConfig(
    private val dbDomainFactory: DbDomainFactory,
    private val ecosSpringEnvironment: EcosWebAppEnvironment
) {

    companion object {
        const val REPO_ID = "ecos-patch-repo"
    }

    @Bean
    fun ecosPatchProperties(): EcosPatchProperties {
        return ecosSpringEnvironment.getValue("ecos-apps.ecos-patch", EcosPatchProperties::class.java)
    }

    @Bean
    fun patchesRepoDao(dataSource: DataSource): RecordsDao {

        val records = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(REPO_ID)
                        withTypeRef(TypeUtils.getTypeRef("ecos-patch"))
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withAuthEnabled(false)
                        withTableRef(DbTableRef("public", "ecos_patch"))
                        withTransactional(false)
                        withStoreTableMeta(true)
                    }
                )
                .build()
        ).build()

        return records
    }
}
