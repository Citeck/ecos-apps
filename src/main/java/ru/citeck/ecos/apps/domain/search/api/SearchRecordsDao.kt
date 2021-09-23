package ru.citeck.ecos.apps.domain.search.api

import org.springframework.stereotype.Component
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.Consistency
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import java.time.Instant

@Component
class SearchRecordsDao(
    private val recordsService: RecordsService
) : RecordsQueryDao {

    companion object {
        const val ID = "search"

        private const val DISP_NAME_ALIAS = "dispName"
        private const val ECOS_TYPE_ALIAS = "ecosType"
        private const val CREATED_ALIAS = "created"
        private const val MODIFIED_ALIAS = "modified"

        private const val GROUP_TYPE_PEOPLE = "PEOPLE"
        private const val GROUP_TYPE_DOCUMENTS = "DOCUMENTS"

        private const val MAX_ITEMS_FOR_TYPE = 50
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {

        val query = recsQuery.getQueryOrNull(SearchQuery::class.java) ?: return null
        if (query.text.isBlank()) {
            return null
        }

        val maxItems = recsQuery.page.maxItems
        val maxItemsForType = query.maxItemsForType.coerceAtMost(MAX_ITEMS_FOR_TYPE)

        val records = ArrayList<SearchRecord>()

        for (type in query.types) {
            when (type) {
                GROUP_TYPE_PEOPLE -> records.addAll(queryPersons(query.text, maxItemsForType))
                GROUP_TYPE_DOCUMENTS -> records.addAll(queryDocuments(query.text, maxItemsForType))
            }
            if (maxItems >= 0 && records.size >= maxItems) {
                break
            }
        }

        return if (maxItems >= 0 && records.size > maxItems) {
            records.subList(0, maxItems - 1)
        } else {
            records
        }
    }

    private fun queryDocuments(text: String, maxItems: Int): List<SearchRecord> {
        return queryImpl(
            sourceId = "alfresco/",
            language = PredicateService.LANGUAGE_PREDICATE,
            query = Predicates.contains("ALL", text),
            maxItems,
            GROUP_TYPE_DOCUMENTS
        )
    }

    private fun queryPersons(text: String, maxItems: Int): List<SearchRecord> {
        return queryImpl(
            sourceId = "alfresco/people",
            language = "fts-alfresco",
            query = "TYPE:\"cm:person\" AND (" +
                "@cm:userName:\"*$text*\" " +
                "OR @cm:firstName:\"*$text*\" " +
                "OR @cm:lastName:\"*$text*\"" +
            ")",
            maxItems,
            GROUP_TYPE_PEOPLE
        )
    }

    private fun queryImpl(sourceId: String,
                          language: String,
                          query: Any, maxItems: Int,
                          groupType: String): List<SearchRecord> {

        val recsQuery = RecordsQuery.create()
            .withQuery(query)
            .withConsistency(Consistency.EVENTUAL)
            .withLanguage(language)
            .withMaxItems(maxItems)
            .withSourceId(sourceId)
            .build()

        val results = recordsService.query(
            recsQuery,
            mapOf(
                DISP_NAME_ALIAS to ScalarType.DISP.schema,
                ECOS_TYPE_ALIAS to RecordConstants.ATT_TYPE + ScalarType.ID.schema,
                CREATED_ALIAS to RecordConstants.ATT_CREATED,
                MODIFIED_ALIAS to RecordConstants.ATT_MODIFIED
            )
        )

        return results.getRecords().map {
            SearchRecord(
                it.getId(),
                it.getAtt(DISP_NAME_ALIAS).asText(),
                groupType,
                RecordRef.valueOf(it.getAtt(ECOS_TYPE_ALIAS).asText()),
                getInstant(it, MODIFIED_ALIAS),
                getInstant(it, CREATED_ALIAS)
            )
        }
    }

    private fun getInstant(atts: RecordAtts, name: String): Instant {
        val result = atts.getAtt(name)
        if (result.isNull() || (result.isTextual() && result.asText().isBlank())) {
            return Instant.EPOCH
        }
        return result.getAs(Instant::class.java) ?: Instant.EPOCH
    }

    override fun getId(): String {
        return ID
    }

    class SearchQuery(
        val text: String,
        val types: List<String> = emptyList(),
        val maxItemsForType: Int = 5
    )

    class SearchRecord(
        val rec: RecordRef,
        private val displayName: String,
        val groupType: String,
        private val ecosType: RecordRef,
        private val modified: Instant,
        private val created: Instant
    ) {
        fun getId(): RecordRef {
            return rec
        }

        fun getDisplayName(): String {
            return displayName
        }

        fun getEcosType(): RecordRef {
            return ecosType
        }

        @AttName(RecordConstants.ATT_CREATED)
        fun getCreated(): Instant {
            return created
        }

        @AttName(RecordConstants.ATT_MODIFIED)
        fun getModified(): Instant {
            return modified
        }
    }
}
