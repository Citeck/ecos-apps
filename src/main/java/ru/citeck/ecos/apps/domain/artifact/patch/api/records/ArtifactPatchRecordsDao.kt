package ru.citeck.ecos.apps.domain.artifact.patch.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.common.AppSystemArtifactPerms
import ru.citeck.ecos.apps.domain.artifact.patch.dto.ArtifactPatchDto
import ru.citeck.ecos.apps.domain.artifact.patch.service.EcosArtifactsPatchService
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDtoDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.perms.RecordPerms

@Component
class ArtifactPatchRecordsDao(
    private val artifactPatchService: EcosArtifactsPatchService,
    private val perms: AppSystemArtifactPerms
) : RecordsQueryDao, RecordMutateDtoDao<ArtifactPatchRecordsDao.RecordToMutate>, RecordAttsDao {

    companion object {
        const val ID = "artifact-patch"
    }

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
        ).map { ArtifactPatchRecord(it) }

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
        return getPatchDtoById(recordId)?.let { ArtifactPatchRecord(it) }
    }

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
        return ID
    }

    inner class ArtifactPatchRecord(
        @AttName("...")
        val dto: ArtifactPatchDto
    ) {
        fun getAsJson(): Any {
            val json = Json.mapper.toNonDefaultData(dto)
            json.remove("sourceType")
            return json
        }

        fun getEcosType(): Any = "ecos-artifact-patch"

        fun getPermissions(): RecordPerms = perms.getPerms(EntityRef.create(AppName.EAPPS, ID, dto.id))
    }

    class RecordToMutate : ArtifactPatchDto {

        var initialId = ""

        constructor()
        constructor(other: ArtifactPatchDto) : super(other) {
            this.initialId = other.id
        }
    }
}
