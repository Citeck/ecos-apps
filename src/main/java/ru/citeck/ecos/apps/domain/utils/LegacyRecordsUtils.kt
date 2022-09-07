package ru.citeck.ecos.apps.domain.utils

import ru.citeck.ecos.records2.request.query.SortBy

object LegacyRecordsUtils {

    @JvmStatic
    fun mapLegacySortBy(sort: List<SortBy>): List<ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy> {
        return sort.map { mapLegacySortBy(it) }
    }

    @JvmStatic
    fun mapLegacySortBy(sort: SortBy): ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy {
        val res = ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy.create()
        res.ascending = sort.isAscending
        res.attribute = sort.attribute
        return res.build()
    }
}
