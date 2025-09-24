package ru.citeck.ecos.apps.domain.patch.config

import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.apps.domain.patch.desc.EcosPatchDesc
import ru.citeck.ecos.apps.domain.patch.eapps.EcosPatchArtifact
import ru.citeck.ecos.apps.domain.patch.service.EcosPatchProperties
import ru.citeck.ecos.apps.domain.patch.service.EcosPatchStatus
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.listener.DbRecordChangedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordCreatedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordsListenerAdapter
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsServiceFactory
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.mixin.AttMixin
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment
import javax.sql.DataSource

@Configuration
class EcosPatchConfig(
    private val dbDomainFactory: DbDomainFactory,
    private val ecosSpringEnvironment: EcosWebAppEnvironment,
    private val recordsServiceFactory: RecordsServiceFactory
) {

    companion object {
        private const val REPO_ID = "${EcosPatchDesc.SRC_ID}-repo"
    }

    @Bean
    fun ecosPatchProperties(): EcosPatchProperties {
        return ecosSpringEnvironment.getValue("ecos-apps.ecos-patch", EcosPatchProperties::class.java)
    }

    @Bean
    fun patchesProxyDao(): RecordsDao {
        return object : RecordsDaoProxy(EcosPatchDesc.SRC_ID, REPO_ID) {
            override fun mutate(records: List<LocalRecordAtts>): List<String> {
                val newRecs = records.map { record ->
                    var recId = record.id
                    var atts = record.getAtts()
                    if (record.id.isEmpty()) {
                        val artifact = record.getAtts().getAsNotNull(EcosPatchArtifact::class.java)
                        val existingConfig = recordsService.queryOne(
                            RecordsQuery.create {
                                withSourceId(EcosPatchDesc.SRC_ID)
                                withQuery(
                                    Predicates.and(
                                        Predicates.eq(EcosPatchDesc.ATT_PATCH_ID, artifact.id),
                                        Predicates.eq(EcosPatchDesc.ATT_TARGET_APP, artifact.targetApp)
                                    )
                                )
                            }
                        )
                        val newAtts = ObjectData.create(artifact)
                        if (artifact.dependsOn.isNotEmpty()) {
                            newAtts[EcosPatchDesc.ATT_DEPENDS_ON] = artifact.getDependsOnWithApp()
                        }
                        newAtts.remove("id")
                        if (existingConfig == null) {
                            newAtts[EcosPatchDesc.ATT_PATCH_ID] = artifact.id
                            newAtts[EcosPatchDesc.ATT_TARGET_APP] = artifact.targetApp
                        } else {
                            recId = existingConfig.getLocalId()
                            newAtts.remove(EcosPatchDesc.ATT_TARGET_APP)
                        }
                        atts = newAtts
                    }
                    LocalRecordAtts(recId, atts)
                }
                return super.mutate(newRecs)
            }
        }
    }

    @Bean
    fun patchesRepoDao(dataSource: DataSource): RecordsDao {

        val recordsDao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(REPO_ID)
                        withTypeRef(ModelUtils.getTypeRef("ecos-patch"))
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withTable("ecos_patch")
                        withStoreTableMeta(true)
                    }
                )
                .build()
        ).withSchema("public")
            .build()

        recordsDao.addAttributesMixin(EcosPatchesMixin())

        recordsDao.addListener(object : DbRecordsListenerAdapter() {
            override fun onCreated(event: DbRecordCreatedEvent) {
                setAttsBeforeCommit(event.globalRef, mapOf(
                    EcosPatchDesc.ATT_STATE to ObjectData.create(),
                    EcosPatchDesc.ATT_STATUS to EcosPatchStatus.PENDING,
                    EcosPatchDesc.ATT_ERRORS_COUNT to 0
                ))
            }
            override fun onChanged(event: DbRecordChangedEvent) {
                val dateBefore = DataValue.of(event.before[EcosPatchDesc.ATT_DATE]).getAsInstantOrEpoch()
                val dateAfter = DataValue.of(event.after[EcosPatchDesc.ATT_DATE]).getAsInstantOrEpoch()

                if (dateAfter.isAfter(dateBefore)) {
                    setAttsBeforeCommit(event.globalRef, mapOf(EcosPatchDesc.ATT_STATUS to EcosPatchStatus.PENDING))
                }
            }

            private fun setAttsBeforeCommit(ref: EntityRef, atts: Map<String, Any>) {

                val attsToMut = TxnContext.getTxnOrNull()?.getData(
                    EcosPatchConfig::class.qualifiedName + ".atts-to-mut"
                ) { LinkedHashMap<EntityRef, MutableMap<String, Any>>() } ?: LinkedHashMap()

                val isFirstCall = attsToMut.isEmpty()
                attsToMut.computeIfAbsent(ref) { LinkedHashMap() }.putAll(atts)

                if (isFirstCall) {
                    TxnContext.doBeforeCommit(0f) {
                        AuthContext.runAsSystem {
                            val mutRecords = attsToMut.entries.map {
                                RecordAtts(it.key, ObjectData.create(it.value))
                            }
                            recordsServiceFactory.recordsService.mutate(mutRecords)
                        }
                    }
                }
            }
        })

        return recordsDao
    }

    private class EcosPatchesMixin : AttMixin {

        val providedAtts = setOf(
            ScalarType.JSON.mirrorAtt
        )

        override fun getAtt(path: String, value: AttValueCtx): Any? {

            return when (path) {
                ScalarType.JSON.mirrorAtt -> {
                    val jsonData = value.getAtt("?json")
                    val patchId = jsonData[EcosPatchDesc.ATT_PATCH_ID].asText()
                    jsonData["id"] = patchId
                    val dto = jsonData.getAsNotNull(EcosPatchArtifact::class.java)
                    val nonDefaultJson = Json.mapper.toNonDefaultJson(dto) as ObjectNode
                    if (dto.name.getValues().size == 1 && dto.name.getClosest() == dto.id) {
                        nonDefaultJson.remove(EcosPatchDesc.ATT_NAME)
                    }
                    nonDefaultJson
                }
                else -> null
            }
        }

        override fun getProvidedAtts(): Collection<String> {
            return providedAtts
        }
    }
}
