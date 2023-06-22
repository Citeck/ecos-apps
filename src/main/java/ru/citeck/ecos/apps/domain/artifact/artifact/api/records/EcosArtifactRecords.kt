package ru.citeck.ecos.apps.domain.artifact.artifact.api.records

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.api.GetModelTypeArtifactsCommand
import ru.citeck.ecos.apps.app.api.GetModelTypeArtifactsCommandResponse
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.domain.artifact.application.service.EcosApplicationsService
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.ArtifactRevSourceType
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.DeployStatus
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.EcosArtifactDto
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypeContext
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService
import ru.citeck.ecos.apps.domain.ecosapp.api.records.EcosAppRecords
import ru.citeck.ecos.apps.domain.utils.LegacyRecordsUtils
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commands.dto.CommandResult
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.records2.QueryContext
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt
import ru.citeck.ecos.records2.graphql.meta.value.EmptyValue
import ru.citeck.ecos.records2.graphql.meta.value.MetaField
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.ValuePredicate
import ru.citeck.ecos.records2.request.query.RecordsQuery
import ru.citeck.ecos.records2.request.query.RecordsQueryResult
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class EcosArtifactRecords(
    val ecosArtifactsService: EcosArtifactsService,
    private val commandsService: CommandsService,
    private val ecosArtifactTypesService: EcosArtifactTypesService,
    private val applicationsService: EcosApplicationsService
) : LocalRecordsDao(),
    LocalRecordsMetaDao<Any>,
    LocalRecordsQueryWithMetaDao<Any> {

    companion object {
        const val ID = "artifact"
        private const val ECOS_APP_REF_ATTRIBUTE = "ecosAppRef"
        private const val ECOS_APP_ATTRIBUTE = "ecosApp"

        private val log = KotlinLogging.logger {}
    }

    init {
        id = ID
    }

    override fun getLocalRecordsMeta(records: List<RecordRef>, metaField: MetaField): List<Any> {

        return records.map {
            ecosArtifactsService.getLastArtifact(ArtifactRef.valueOf(it.id))
        }.map {
            if (it == null) {
                EmptyValue.INSTANCE
            } else {
                EcosArtifactRecord(it, ecosArtifactTypesService.getTypeContext(it.type))
            }
        }
    }

    override fun queryLocalRecords(recordsQuery: RecordsQuery, field: MetaField): RecordsQueryResult<Any> {

        val result = RecordsQueryResult<Any>()

        if (recordsQuery.language == "type-artifacts") {

            val typeArtifactsQuery = recordsQuery.getQuery(EcosAppRecords.TypeArtifactsQuery::class.java)
            val artifacts = getArtifactsForTypes(typeArtifactsQuery.typeRefs, HashSet())
            artifacts.removeAll(typeArtifactsQuery.typeRefs)

            result.records = artifacts.map {
                val type = ecosArtifactTypesService.getTypeIdForRecordRef(it)
                var moduleRes: Any = EmptyValue.INSTANCE
                if (type.isNotEmpty()) {
                    val artifact = ecosArtifactsService.getLastArtifact(ArtifactRef.create(type, it.getLocalId()))
                    if (artifact != null && !artifact.system) {
                        moduleRes = EcosArtifactRecord(artifact, ecosArtifactTypesService.getTypeContext(artifact.type))
                    }
                }
                moduleRes
            }.filter { it !== EmptyValue.INSTANCE }
        } else if (recordsQuery.language == PredicateService.LANGUAGE_PREDICATE) {

            var predicate = recordsQuery.getQuery(Predicate::class.java)
            predicate = PredicateUtils.mapValuePredicates(predicate) {
                if (it.getAttribute() == ECOS_APP_REF_ATTRIBUTE) {
                    val appName = EntityRef.valueOf(it.getValue().asText()).getLocalId()
                    ValuePredicate(ECOS_APP_ATTRIBUTE, it.getType(), appName)
                } else {
                    it
                }
            }
            val res = ecosArtifactsService.getAllArtifacts(
                predicate,
                recordsQuery.maxItems,
                recordsQuery.skipCount,
                LegacyRecordsUtils.mapLegacySortBy(recordsQuery.sortBy)
            )
            result.records = res.map { EcosArtifactRecord(it, ecosArtifactTypesService.getTypeContext(it.type)) }
            result.totalCount = ecosArtifactsService.getAllArtifactsCount(predicate)
        }

        return result
    }

    private fun getArtifactsForTypes(
        typeRefs: Collection<EntityRef>,
        checkedTypes: MutableSet<EntityRef>
    ): MutableSet<EntityRef> {

        if (typeRefs.isEmpty()) {
            return mutableSetOf()
        }

        checkedTypes.addAll(typeRefs)

        val expandTypesQuery = RecordsQuery().apply {

            this.sourceId = "emodel/type"
            this.language = "expand-types"

            val query = HashMap<String, Any>()
            query["typeRefs"] = typeRefs
            this.query = query
        }

        val expandedTypes = recordsService.queryRecords(expandTypesQuery).records
        if (expandedTypes.isEmpty()) {
            return mutableSetOf()
        }

        val artifactsSet = HashSet<EntityRef>()
        val newTypes = HashSet<EntityRef>()

        val appNames = applicationsService.getAppsStatus().keys.toList()
        val resultFutures = appNames.map {
            commandsService.execute {
                body = GetModelTypeArtifactsCommand(expandedTypes)
                targetApp = it
            }
        }

        val results = mutableListOf<CommandResult>()
        for (future in resultFutures.withIndex()) {
            try {
                results.add(future.value.get(2, TimeUnit.SECONDS))
            } catch (e: TimeoutException) {
                log.warn("Service doesn't respond in 2 seconds: '${appNames[future.index]}'")
            } catch (e: Exception) {
                log.error(e) { "Exception while future waiting for app: '${appNames[future.index]}'" }
            }
        }

        results.mapNotNull {
            it.getResultAs(GetModelTypeArtifactsCommandResponse::class.java)?.artifacts
        }.forEach {
            artifactsSet.addAll(it)
            it.forEach { ref ->
                if (ref.getAppName() == "emodel" && ref.getSourceId() == "type" && checkedTypes.add(ref)) {
                    newTypes.add(ref)
                }
            }
        }

        if (newTypes.isNotEmpty()) {
            artifactsSet.addAll(getArtifactsForTypes(newTypes, checkedTypes))
        }

        return artifactsSet
    }

    inner class EcosArtifactRecord(
        val artifact: EcosArtifactDto,
        private val typeContext: EcosArtifactTypeContext?
    ) {

        fun getId(): String {
            return ArtifactRef.create(artifact.type, artifact.id).toString()
        }

        fun getModuleId(): String {
            return artifact.id
        }

        fun getData(): ByteArray {
            return ecosArtifactsService.getArtifactData(ArtifactRef.create(artifact.type, artifact.id))
        }

        fun getType(): Any? {
            return if (typeContext != null) {
                ArtifactTypeInfo(typeContext)
            } else {
                artifact.type
            }
        }

        fun getName(): MLText? {
            return artifact.name
        }

        @MetaAtt(".type")
        fun getEcosType(): RecordRef {
            return RecordRef.valueOf("emodel/type@ecos-artifact")
        }

        @MetaAtt(".disp")
        fun getDisplayName(): String {

            val locale = QueryContext.getCurrent<QueryContext>().locale

            var name = MLText.getClosestValue(artifact.name, locale)
            if (name.isBlank()) {
                name = artifact.id
            }

            var typeName = MLText.getClosestValue(typeContext?.getMeta()?.name, locale)
            if (typeName.isBlank()) {
                typeName = artifact.type
            }
            return "$typeName: $name"
        }

        fun getTagsStr(): String {
            return artifact.tags.joinToString()
        }

        fun getSourceType(): ArtifactRevSourceType {
            return artifact.source.type
        }

        fun getSourceId(): String {
            return artifact.source.id
        }

        fun getDeployStatus(): DeployStatus {
            return artifact.deployStatus
        }

        fun getTags(): List<String> {
            return artifact.tags
        }

        fun getModifiedIso(): String {
            return (artifact.modified ?: Instant.EPOCH).toString()
        }

        fun getCreatedIso(): String {
            return (artifact.created ?: Instant.EPOCH).toString()
        }

        fun getEcosAppRef(): EntityRef {
            if (artifact.ecosApp.isBlank()) {
                return EntityRef.EMPTY
            }
            return EntityRef.create(AppName.EAPPS, EcosAppRecords.ID, artifact.ecosApp)
        }
    }

    class ArtifactTypeInfo(private val typeContext: EcosArtifactTypeContext) {

        @MetaAtt(".disp")
        fun getDisplayName(): String {
            val locale = QueryContext.getCurrent<QueryContext>().locale
            val name = MLText.getClosestValue(typeContext.getMeta().name, locale)
            return if (name.isNotBlank()) {
                name
            } else {
                typeContext.getId()
            }
        }

        @MetaAtt(".str")
        fun getString(): String {
            return typeContext.getId()
        }
    }
}
