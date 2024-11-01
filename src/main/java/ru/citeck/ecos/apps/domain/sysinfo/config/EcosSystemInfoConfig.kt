package ru.citeck.ecos.apps.domain.sysinfo.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records3.record.dao.RecordsDao
import javax.sql.DataSource

@Configuration
class EcosSystemInfoConfig(
    private val dbDomainFactory: DbDomainFactory
) {

    companion object {
        const val SYS_INFO_REPO_ID = "system-info-repo"
    }

    @Bean
    fun systemInfoRepoDao(dataSource: DataSource): RecordsDao {

        val records = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(SYS_INFO_REPO_ID)
                        withTypeRef(ModelUtils.getTypeRef("system-info"))
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withTable("ecos_system_info")
                        withStoreTableMeta(true)
                    }
                )
                .build()
        ).withSchema("public")
            .withPermsComponent(object : DbPermsComponent {

                override fun getRecordPerms(user: String, authorities: Set<String>, record: Any): DbRecordPerms {
                    val isAdminOrSystem = authorities.contains(AuthRole.ADMIN) || authorities.contains(AuthRole.SYSTEM)
                    return object : DbRecordPerms {
                        override fun getAdditionalPerms(): Set<String> {
                            return emptySet()
                        }
                        override fun getAuthoritiesWithReadPermission(): Set<String> {
                            return setOf(AuthRole.ADMIN)
                        }
                        override fun hasAttReadPerms(name: String): Boolean {
                            return isAdminOrSystem
                        }
                        override fun hasAttWritePerms(name: String): Boolean {
                            return isAdminOrSystem
                        }
                        override fun hasReadPerms(): Boolean {
                            return true
                        }
                        override fun hasWritePerms(): Boolean {
                            return isAdminOrSystem
                        }
                    }
                }
            }).build()

        return records
    }
}
