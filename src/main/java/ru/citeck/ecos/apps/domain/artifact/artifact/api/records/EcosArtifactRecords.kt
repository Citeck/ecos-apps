package ru.citeck.ecos.apps.domain.artifact.artifact.api.records

import io.github.oshai.kotlinlogging.KotlinLogging
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
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commands.dto.CommandResult
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records2.predicate.model.ValuePredicate
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.atts.value.impl.EmptyAttValue
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
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
) : AbstractRecordsDao(), RecordsQueryDao, RecordAttsDao {

    companion object {
        const val ID = "artifact"
        const val ECOS_APP_REF_ATTRIBUTE = "ecosAppRef"
        private const val ECOS_APP_ATTRIBUTE = "ecosApp"

        private val log = KotlinLogging.logger {}
    }

    override fun getRecordAtts(recordId: String): Any? {
        return ecosArtifactsService.getLastArtifact(ArtifactRef.valueOf(recordId))?.let {
            EcosArtifactRecord(it, ecosArtifactTypesService.getTypeContext(it.type))
        } ?: EmptyAttValue.INSTANCE
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {

        val result = RecsQueryRes<Any>()

        if (recsQuery.language == "type-artifacts") {

            val typeArtifactsQuery = recsQuery.getQuery(EcosAppRecords.TypeArtifactsQuery::class.java)
            val artifacts = getArtifactsForTypes(typeArtifactsQuery.typeRefs, HashSet())
            artifacts.removeAll(typeArtifactsQuery.typeRefs)

            result.setRecords(
                artifacts.map {
                    val type = ecosArtifactTypesService.getTypeIdForRecordRef(it)
                    var moduleRes: Any = EmptyAttValue.INSTANCE
                    if (type.isNotEmpty()) {
                        val artifact = ecosArtifactsService.getLastArtifact(ArtifactRef.create(type, it.getLocalId()))
                        if (artifact != null && !artifact.system) {
                            moduleRes = EcosArtifactRecord(artifact, ecosArtifactTypesService.getTypeContext(artifact.type))
                        }
                    }
                    moduleRes
                }.filter { it !== EmptyAttValue.INSTANCE }
            )
        } else if (recsQuery.language == PredicateService.LANGUAGE_PREDICATE) {

            var predicate = recsQuery.getQuery(Predicate::class.java)
            predicate = PredicateUtils.mapValuePredicates(predicate) {
                if (it.getAttribute() == ECOS_APP_REF_ATTRIBUTE) {
                    val appName = EntityRef.valueOf(it.getValue().asText()).getLocalId()
                    ValuePredicate(ECOS_APP_ATTRIBUTE, it.getType(), appName)
                } else {
                    it
                }
            } ?: Predicates.alwaysTrue()
            val res = ecosArtifactsService.getAllArtifacts(
                predicate,
                recsQuery.page.maxItems,
                recsQuery.page.skipCount,
                recsQuery.sortBy
            )
            result.setRecords(res.map { EcosArtifactRecord(it, ecosArtifactTypesService.getTypeContext(it.type)) })
            result.setTotalCount(ecosArtifactsService.getAllArtifactsCount(predicate))
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
        val query = HashMap<String, Any>()
        query["typeRefs"] = typeRefs

        val expandTypesQuery = RecordsQuery.create()
            .withSourceId("emodel/type")
            .withLanguage("expand-types")
            .withQuery(query)
            .build()

        val expandedTypes = recordsService.query(expandTypesQuery).getRecords()
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
                log.warn { "Service doesn't respond in 2 seconds: '${appNames[future.index]}'" }
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

    override fun getId(): String {
        return ID
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

        @AttName("_type")
        fun getEcosType(): EntityRef {
            return EntityRef.valueOf("emodel/type@ecos-artifact")
        }

        @AttName("?disp")
        fun getDisplayName(): String {

            val locale = I18nContext.getLocale()
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

        @AttName("?disp")
        fun getDisplayName(): String {
            val locale = I18nContext.getLocale()
            val name = MLText.getClosestValue(typeContext.getMeta().name, locale)
            return name.ifBlank { typeContext.getId() }
        }

        @AttName("?str")
        fun getString(): String {
            return typeContext.getId()
        }
    }
}
