package ru.citeck.ecos.apps.domain.config.eapps

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.apps.domain.config.dto.ConfigDef
import ru.citeck.ecos.apps.domain.config.service.EcosConfigAppConstants
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.config.lib.artifact.provider.ConfigArtifactUtils
import ru.citeck.ecos.config.lib.dto.ConfigValueDef
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.function.Consumer

@Component
class ConfigArtifactHandler(
    val recordsService: RecordsService
) : EcosArtifactHandler<ObjectData> {

    companion object {
        private const val PROP_VALUE = "value"
    }

    override fun deployArtifact(artifact: ObjectData) {

        ConfigArtifactUtils.fillDefaultProps(artifact.getData())
        val configDef = artifact.getAsNotNull(ConfigDef::class.java)

        val existingConfig = recordsService.queryOne(
            RecordsQuery.create {
                withSourceId("config-repo")
                withQuery(
                    Predicates.and(
                        Predicates.eq("configId", configDef.id),
                        Predicates.eq("scope", configDef.scope)
                    )
                )
            },
            ExistingConfigQueryAtts::class.java
        )

        val recordAtts = RecordAtts()
        if (existingConfig != null) {
            recordAtts.setId(existingConfig.ref)
        } else {
            recordAtts.setId(EntityRef.create("config-repo", ""))
        }

        val attributes = ObjectData.create(configDef)
        attributes.remove("id")

        if (existingConfig == null || configDef.version > existingConfig.version) {
            val valueObj = ObjectData.create()
            valueObj[EcosConfigAppConstants.VALUE_SHORT_PROP] = attributes[PROP_VALUE]
            attributes[PROP_VALUE] = valueObj
        } else {
            attributes[PROP_VALUE] = existingConfig.value
        }
        attributes["configId"] = configDef.id

        recordAtts.setAttributes(attributes)

        recordsService.mutate(recordAtts)
    }

    override fun getArtifactType(): String {
        return "app/config"
    }

    override fun listenChanges(listener: Consumer<ObjectData>) {
    }

    override fun deleteArtifact(artifactId: String) {
        recordsService.delete(EntityRef.create("config", artifactId))
    }

    data class ExistingConfigQueryAtts(
        @AttName("?id")
        val ref: EntityRef,
        val version: Int,
        val value: ObjectData,
        val valueDef: ConfigValueDef
    )
}
