package ru.citeck.ecos.apps.domain.artifact.artifact.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.api.GetEcosTypeArtifactsCommand
import ru.citeck.ecos.apps.app.api.GetEcosTypeArtifactsCommandResponse
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.artifact.type.TypeContext
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.EcosArtifactDto
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypeContext
import ru.citeck.ecos.apps.domain.ecosapp.api.records.EcosAppRecords
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.records2.QueryContext
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt
import ru.citeck.ecos.records2.graphql.meta.value.EmptyValue
import ru.citeck.ecos.records2.graphql.meta.value.MetaField
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.request.query.RecordsQuery
import ru.citeck.ecos.records2.request.query.RecordsQueryResult
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao

@Component
class EcosArtifactRecords(
    private val ecosArtifactsService: EcosArtifactsService,
    private val commandsService: CommandsService,
    private val ecosArtifactTypesService: EcosArtifactTypesService
) : LocalRecordsDao(),
    LocalRecordsMetaDao<Any>,
    LocalRecordsQueryWithMetaDao<Any> {

    companion object {
        const val ID = "artifact"
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
            if (typeArtifactsQuery.typeRefs.isEmpty()) {
                return RecordsQueryResult()
            }

            val expandTypesQuery = RecordsQuery().apply {

                this.sourceId = "emodel/type"
                this.language = "expand-types"

                val query = HashMap<String, Any>()
                query["typeRefs"] = typeArtifactsQuery.typeRefs
                this.query = query
            }

            val expandedTypes = recordsService.queryRecords(expandTypesQuery).records
            if (expandedTypes.isEmpty()) {
                return RecordsQueryResult()
            }

            val results = commandsService.executeForGroupSync {
                body = GetEcosTypeArtifactsCommand(expandedTypes)
                targetApp = "all"
            }

            val artifactsSet = HashSet<RecordRef>()
            results.mapNotNull {
                it.getResultAs(GetEcosTypeArtifactsCommandResponse::class.java)?.artifacts
            }.forEach {
                artifactsSet.addAll(it)
            }

            result.records = artifactsSet.map {
                val type = ecosArtifactTypesService.getTypeIdForRecordRef(it)
                var moduleRes: Any = EmptyValue.INSTANCE
                if (type.isNotEmpty()) {
                    val artifact = ecosArtifactsService.getLastArtifact(ArtifactRef.create(type, it.id))
                    if (artifact != null) {
                        moduleRes = EcosArtifactRecord(artifact, ecosArtifactTypesService.getTypeContext(artifact.type))
                    }
                }
                moduleRes
            }.filter { it !== EmptyValue.INSTANCE }

        } else if (recordsQuery.language == PredicateService.LANGUAGE_PREDICATE) {

            val predicate = recordsQuery.getQuery(Predicate::class.java);
            val res = ecosArtifactsService.getAllArtifacts(
                predicate,
                recordsQuery.skipCount,
                recordsQuery.maxItems
            )
            result.records = res.map { EcosArtifactRecord(it, ecosArtifactTypesService.getTypeContext(it.type)) }
            result.totalCount = ecosArtifactsService.getAllArtifactsCount(predicate)
        }

        return result
    }

    class EcosArtifactRecord(
        val artifact: EcosArtifactDto,
        private val typeContext: EcosArtifactTypeContext?
    ) {

        fun getId(): String {
            return ArtifactRef.create(artifact.type, artifact.id).toString()
        }

        fun getModuleId(): String {
            return artifact.id
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

            val name = MLText.getClosestValue(artifact.name, locale)

            var typeName = MLText.getClosestValue(typeContext?.getMeta()?.name, locale)
            if (typeName.isBlank()) {
                typeName = artifact.type
            }
            return "$typeName: $name"
        }

        fun getTagsStr(): String {
            return artifact.tags.joinToString()
        }

        fun getTags(): List<String> {
            return artifact.tags
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
