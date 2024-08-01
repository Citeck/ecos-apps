package ru.citeck.ecos.apps.domain.devtools.devmodule.service

import org.springframework.stereotype.Service
import ru.citeck.ecos.apps.domain.devtools.devmodule.dto.DevModuleDef
import ru.citeck.ecos.apps.domain.devtools.devmodule.repo.DevModuleEntity
import ru.citeck.ecos.apps.domain.devtools.devmodule.repo.DevModuleRepo
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Service
class DevModulesService(
    val repo: DevModuleRepo
) {

    fun getById(id: String): DevModuleDef? {
        return repo.findByExtId(id)?.let { toDto(it) }
    }

    fun save(def: DevModuleDef) {
        repo.save(toEntity(def))
    }

    fun delete(id: String) {
        repo.findByExtId(id)?.let { repo.delete(it) }
    }

    fun findAll(): List<DevModuleDef> {
        return repo.findAll().map { toDto(it) }
    }

    private fun toDto(entity: DevModuleEntity): DevModuleDef {
        return DevModuleDef.create()
            .withId(entity.extId)
            .withActions(DataValue.create(entity.actions).asList(EntityRef::class.java))
            .withName(Json.mapper.read(entity.name, MLText::class.java))
            .build()
    }

    private fun toEntity(def: DevModuleDef): DevModuleEntity {

        val entity: DevModuleEntity = repo.findByExtId(def.id)
            ?: DevModuleEntity().let {
                it.extId = def.id
                it
            }

        entity.name = Json.mapper.toString(def.name)
        entity.actions = Json.mapper.toString(def.actions)

        return entity
    }
}
