package ru.citeck.ecos.apps.domain.artifact.patch.api.records

import org.springframework.security.access.annotation.Secured
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.domain.artifact.patch.dto.ArtifactPatchDto
import ru.citeck.ecos.apps.domain.artifact.patch.service.EcosArtifactsPatchService
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDtoDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes

@Component
class ArtifactPatchRecordsDao(
    val artifactPatchService: EcosArtifactsPatchService
) : RecordsQueryDao,
    RecordMutateDtoDao<ArtifactPatchRecordsDao.RecordToMutate>,
    RecordAttsDao {

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        if (recsQuery.language != PredicateService.LANGUAGE_PREDICATE) {
            return emptyList<Any>()
        }
        val predicate = recsQuery.getQuery(Predicate::class.java)
        val page = recsQuery.page

        val artifacts = artifactPatchService.getAll(
            page.maxItems,
            page.skipCount,
            predicate,
            recsQuery.sortBy
        ).map { PatchRecord(it) }

        val result = RecsQueryRes<Any>()
        result.addRecords(artifacts)
        result.setTotalCount(artifactPatchService.getCount(predicate))

        return result
    }

    private fun getPatchDtoById(recordId: String): ArtifactPatchDto? {
        if (recordId.isEmpty()) {
            val dto = ArtifactPatchDto()
            dto.id = ""
            dto.config = ObjectData.create()
                .set(
                    "operations",
                    DataValue.createArr()
                        .add(
                            DataValue.createObj()
                                .set("op", "set")
                                .set("path", "$.name")
                                .set("value", "abc")
                        )
                )
            dto.enabled = true
            dto.type = "json"
            return dto
        }
        return artifactPatchService.getPatchById(recordId)
    }

    override fun getRecordAtts(recordId: String): Any? {
        return getPatchDtoById(recordId)?.let { PatchRecord(it) }
    }

    @Secured(AuthRole.ADMIN)
    override fun getRecToMutate(recordId: String): RecordToMutate {
        val dto = getPatchDtoById(recordId) ?: error("Artifact patch can't be found by id: '$recordId'")
        return RecordToMutate(dto)
    }

    override fun saveMutatedRec(record: RecordToMutate): String {
        if (record.id.isBlank()) {
            error("Record id can't be blank")
        }
        if (record.id != record.initialId) {
            val recordByNewId = artifactPatchService.getPatchById(record.id)
            if (recordByNewId != null) {
                error("Patch with id '${record.id}' already exists")
            }
        }
        return artifactPatchService.save(record)?.id ?: error("Patch after saving is null. Record: $record")
    }

    override fun getId(): String {
        return "artifact-patch"
    }

    class PatchRecord(
        @AttName("...")
        val dto: ArtifactPatchDto
    ) {
        fun getAsJson(): Any {
            val json = Json.mapper.toNonDefaultData(dto)
            json.remove("sourceType")
            return json
        }

        fun getEcosType(): Any = "ecos-artifact-patch"
    }

    class RecordToMutate : ArtifactPatchDto {

        var initialId = ""

        constructor()
        constructor(other: ArtifactPatchDto) : super(other) {
            this.initialId = other.id
        }
    }
}
