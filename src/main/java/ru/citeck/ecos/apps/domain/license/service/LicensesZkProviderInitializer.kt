package ru.citeck.ecos.apps.domain.license.service

import mu.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.dto.DbTableRef
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.listener.*
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.license.EcosLicenseInstance
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.mixin.AttMixin
import ru.citeck.ecos.webapp.api.constants.WebAppProfile
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.zookeeper.EcosZooKeeper
import javax.annotation.PostConstruct

@Component
@Profile("!${WebAppProfile.TEST}")
class LicensesZkProviderInitializer(
    val recordsService: RecordsService,
    val dbDomainFactory: DbDomainFactory,
    ecosZooKeeper: EcosZooKeeper
) {

    companion object {
        const val LIC_SRC_ID = "ecos-license"
        const val ATT_IS_LIC_VALID = "isLicenseValid"

        private val log = KotlinLogging.logger {}
    }

    private val zooKeeper = ecosZooKeeper.withNamespace("ecos/licenses")

    @PostConstruct
    fun init() {

        val dao = dbDomainFactory.create(
            DbDomainConfig.create {
                withRecordsDao(
                    DbRecordsDaoConfig.create()
                        .withId(LIC_SRC_ID)
                        .withTypeRef(TypeUtils.getTypeRef("ecos-license"))
                        .build()
                )
                withDataService(
                    DbDataServiceConfig.create()
                        .withAuthEnabled(true)
                        .withTableRef(DbTableRef("public", "ecos_license"))
                        .withTransactional(false)
                        .withStoreTableMeta(true)
                        .build()
                )
            }
        ).withPermsComponent(object : DbPermsComponent {
            override fun getRecordPerms(recordRef: EntityRef): DbRecordPerms {
                return object : DbRecordPerms {
                    override fun getAuthoritiesWithReadPermission(): Set<String> {
                        return setOf(AuthRole.ADMIN)
                    }
                    override fun isCurrentUserHasAttReadPerms(name: String): Boolean {
                        return AuthContext.isRunAsAdmin()
                    }
                    override fun isCurrentUserHasAttWritePerms(name: String): Boolean {
                        return AuthContext.isRunAsAdmin()
                    }
                    override fun isCurrentUserHasWritePerms(): Boolean {
                        return AuthContext.isRunAsAdmin()
                    }
                }
            }
        }).build()

        dao.addAttributesMixin(object : AttMixin {
            override fun getAtt(path: String, value: AttValueCtx): Any {
                return value.getAtt(ScalarType.JSON_SCHEMA).getAs(EcosLicenseInstance::class.java)?.isValid() ?: false
            }
            override fun getProvidedAtts(): Collection<String> {
                return listOf(ATT_IS_LIC_VALID)
            }
        })

        recordsService.register(dao)

        val licensesQueryRes = recordsService.query(
            RecordsQuery.create()
                .withSourceId(LIC_SRC_ID)
                .withMaxItems(5000)
                .withQuery(Predicates.alwaysTrue())
                .build(),
            mapOf("json" to ScalarType.JSON_SCHEMA)
        )

        val licenses = licensesQueryRes.getRecords().mapNotNull {
            it.getAtt("json").getAs(EcosLicenseInstance::class.java)
        }
        log.info { "Licenses found: ${licenses.size}" }
        val licenseIdsInDb = licenses.map { it.id }.toSet()
        val licensesToRemove = zooKeeper.getChildren("/").toMutableSet()
        licensesToRemove.removeAll(licenseIdsInDb)
        licenses.forEach {
            log.info { "Update license from zookeeper: '${it.id}'" }
            zooKeeper.setValue("/${it.id}", it)
        }
        licensesToRemove.forEach {
            log.info { "Delete license from zookeeper: '$it'" }
            zooKeeper.deleteValue("/$it")
        }

        dao.addListener(object : DbRecordsListener {
            override fun onChanged(event: DbRecordChangedEvent) {
                val json = recordsService.getAtt(event.record, ScalarType.JSON_SCHEMA)
                zooKeeper.setValue("/" + json["id"].asText(), json.getAsNotNull(EcosLicenseInstance::class.java))
            }
            override fun onCreated(event: DbRecordCreatedEvent) {
                val json = recordsService.getAtt(event.record, ScalarType.JSON_SCHEMA)
                zooKeeper.setValue("/" + json["id"].asText(), json.getAsNotNull(EcosLicenseInstance::class.java))
            }
            override fun onDeleted(event: DbRecordDeletedEvent) {
                val recId = recordsService.getAtt(event.record, "id").asText()
                zooKeeper.deleteValue("/$recId")
            }
            override fun onDraftStatusChanged(event: DbRecordDraftStatusChangedEvent) {}
            override fun onStatusChanged(event: DbRecordStatusChangedEvent) {}
        })
    }
}
