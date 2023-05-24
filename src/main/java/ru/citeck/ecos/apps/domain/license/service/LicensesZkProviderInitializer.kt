package ru.citeck.ecos.apps.domain.license.service

import mu.KotlinLogging
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.listener.*
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.license.EcosLicenseInstance
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.RecordsServiceFactory
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.atts.value.AttValue
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.atts.value.impl.AttValueDelegate
import ru.citeck.ecos.records3.record.atts.value.impl.EmptyAttValue
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.records3.record.mixin.AttMixin
import ru.citeck.ecos.webapp.api.constants.WebAppProfile
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment
import ru.citeck.ecos.zookeeper.EcosZooKeeper
import javax.annotation.PostConstruct

@Configuration
@Profile("!${WebAppProfile.TEST}")
class LicensesZkProviderInitializer(
    val recordsService: RecordsService,
    val dbDomainFactory: DbDomainFactory,
    ecosZooKeeper: EcosZooKeeper,
    val ecosWebAppEnv: EcosWebAppEnvironment,
    val predicateService: PredicateService,
    val recordsServiceFactory: RecordsServiceFactory
) {

    companion object {
        const val LIC_SRC_ID = "ecos-license"
        const val LIC_REPO_SRC_ID = "ecos-license-repo"
        const val ATT_IS_LIC_VALID = "isLicenseValid"

        private val licTypeRef = ModelUtils.getTypeRef("ecos-license")

        private val log = KotlinLogging.logger {}
    }

    private val licensesFromProps = loadLicensesFromProps()
    private val zooKeeper = ecosZooKeeper.withNamespace("ecos/licenses")

    @PostConstruct
    fun init() {

        val repoDao = dbDomainFactory.create(
            DbDomainConfig.create {
                withRecordsDao(
                    DbRecordsDaoConfig.create()
                        .withId(LIC_REPO_SRC_ID)
                        .withTypeRef(licTypeRef)
                        .build()
                )
                withDataService(
                    DbDataServiceConfig.create()
                        .withTable("ecos_license")
                        .withStoreTableMeta(true)
                        .build()
                )
            }
        ).withSchema("public")
            .withPermsComponent(object : DbPermsComponent {

                override fun getRecordPerms(user: String, authorities: Set<String>, record: Any): DbRecordPerms {
                    val isAdmin = authorities.contains(AuthRole.ADMIN)
                    return object : DbRecordPerms {
                        override fun getAuthoritiesWithReadPermission(): Set<String> {
                            return setOf(AuthRole.ADMIN)
                        }
                        override fun hasAttReadPerms(name: String): Boolean {
                            return isAdmin
                        }
                        override fun hasAttWritePerms(name: String): Boolean {
                            return isAdmin
                        }
                        override fun hasReadPerms(): Boolean {
                            return isAdmin
                        }
                        override fun hasWritePerms(): Boolean {
                            return isAdmin
                        }
                    }
                }
            }).build()

        repoDao.addAttributesMixin(object : AttMixin {
            override fun getAtt(path: String, value: AttValueCtx): Any {
                return value.getAtt(ScalarType.JSON_SCHEMA)
                    .getAs(EcosLicenseInstance::class.java)?.isValid() ?: false
            }
            override fun getProvidedAtts(): Collection<String> {
                return listOf(ATT_IS_LIC_VALID)
            }
        })

        val proxyDao = LicDaoProxy()
        recordsService.register(repoDao)
        recordsService.register(proxyDao)

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

        repoDao.addListener(object : DbRecordsListenerAdapter() {
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
        })
    }

    private fun loadLicensesFromProps(): List<EcosLicenseInstance> {
        val props = ecosWebAppEnv.getValue("ecos.webapp.license", LicenseProps::class.java)
        return props.instances.map {
            Json.mapper.readNotNull(it, EcosLicenseInstance::class.java)
        }.filter { it.id.isNotBlank() }
    }

    private inner class LicDaoProxy : RecordsDaoProxy(LIC_SRC_ID, LIC_REPO_SRC_ID) {

        override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<*>? {

            if (!AuthContext.isRunAsSystemOrAdmin() ||
                recsQuery.language != "" && recsQuery.language != PredicateService.LANGUAGE_PREDICATE
            ) {
                return null
            }
            if (licensesFromProps.isEmpty()) {
                return super.queryRecords(recsQuery)
            }
            val srcPredicate = if (recsQuery.query.isEmpty()) {
                Predicates.alwaysTrue()
            } else {
                recsQuery.getQuery(Predicate::class.java)
            }
            val predicateForPropsLic = PredicateUtils.mapAttributePredicates(srcPredicate) {
                if (it.getAttribute().startsWith("_")) {
                    Predicates.alwaysTrue()
                } else {
                    it
                }
            } ?: Predicates.alwaysTrue()

            val filteredLicensesFromProps = predicateService.filter(
                licensesFromProps.map { mapLicInstanceToAttValue(it) },
                predicateForPropsLic
            )
            if (filteredLicensesFromProps.isEmpty()) {
                return super.queryRecords(recsQuery)
            }

            var skipCount = recsQuery.page.skipCount
            val skippedLicFromProps = if (skipCount > 0) {
                if (skipCount >= filteredLicensesFromProps.size) {
                    skipCount = 0
                    emptyList()
                } else {
                    skipCount -= filteredLicensesFromProps.size
                    filteredLicensesFromProps.drop(skipCount)
                }
            } else {
                filteredLicensesFromProps
            }
            val recordsRes = super.queryRecords(recsQuery.copy {
                withSkipCount(skipCount)
                withQuery(Predicates.and(
                    srcPredicate,
                    Predicates.not(Predicates.inVals("id", licensesFromProps.map { it.id }))
                ))
            }) ?: RecsQueryRes<Any>()

            if (skippedLicFromProps.isEmpty()) {
                return recordsRes
            }

            var records: MutableList<Any> = ArrayList(skippedLicFromProps)
            records.addAll(recordsRes.getRecords())
            val maxItems = recsQuery.page.maxItems
            if (maxItems >= 0 && records.size > maxItems) {
                records = records.subList(0, maxItems)
            }
            val totalCount = recordsRes.getTotalCount() + skippedLicFromProps.size

            val result = RecsQueryRes(records)
            result.setTotalCount(totalCount)

            return result
        }

        override fun getRecordsAtts(recordIds: List<String>): List<*>? {
            if (licensesFromProps.isEmpty()) {
                return super.getRecordsAtts(recordIds)
            }
            return recordIds.map { recId ->
                licensesFromProps.find { it.id == recId }?.let { mapLicInstanceToAttValue(it) }
                    ?: super.getRecordsAtts(listOf(recId))?.get(0)
            }
        }

        override fun delete(recordIds: List<String>): List<DelStatus> {
            if (recordIds.any { recId -> licensesFromProps.any { it.id == recId } }) {
                error("You can't delete license loaded from system properties")
            }
            return super.delete(recordIds)
        }

        override fun mutate(records: List<LocalRecordAtts>): List<String> {
            for (record in records) {
                val recId = record.id.ifEmpty {
                    record.getAtt("id").asText()
                }
                if (recId.isBlank()) {
                    error("Invalid license id: '$recId'")
                }
                if (licensesFromProps.any { it.id == recId }) {
                    error("You can't change license loaded from system properties")
                }
            }
            return super.mutate(records)
        }
    }

    private fun mapLicInstanceToAttValue(license: EcosLicenseInstance): LicFromPropsAttValue {
        val attValue = recordsServiceFactory.attValuesConverter.toAttValue(license) ?: EmptyAttValue.INSTANCE
        return LicFromPropsAttValue(license, attValue)
    }

    class LicenseProps(
        val instances: List<String> = emptyList()
    )

    class LicFromPropsAttValue(
        @AttName("...")
        val license: EcosLicenseInstance,
        value: AttValue
    ) : AttValueDelegate(value) {

        override fun getDisplayName(): Any {
            val prefix = if (I18nContext.getLocale() == I18nContext.RUSSIAN) {
                "Лицензия: "
            } else {
                "License: "
            }
            return prefix + license.id
        }

        override fun getAtt(name: String): Any? {
            if (name == ATT_IS_LIC_VALID) {
                return license.isValid()
            }
            if (name == "permissions") {
                return Perms()
            }
            return super.getAtt(name)
        }

        override fun getType(): Any {
            return licTypeRef
        }

        inner class Perms : AttValue {
            override fun has(name: String): Boolean {
                if (name.equals("Read", true)) {
                    return true
                }
                return false
            }
        }
    }
}
