package ru.citeck.ecos.apps.domain.config.eapps

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.apps.domain.config.service.EcosConfigAppConstants
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.config.lib.artifact.dto.ConfigDef
import ru.citeck.ecos.config.lib.dto.ConfigKey
import ru.citeck.ecos.config.lib.zookeeper.ZkConfigService
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import java.util.function.Consumer

@Component
class ConfigArtifactHandler(
    val recordsService: RecordsService,
    val zkConfigService: ZkConfigService
) : EcosArtifactHandler<ConfigDef> {

    companion object {
        private const val PROP_VALUE = "value"
    }

    override fun deployArtifact(artifact: ConfigDef) {

        val existingConfig = recordsService.queryOne(RecordsQuery.create {
            withSourceId("config-repo")
            withQuery(Predicates.and(
                Predicates.eq("configId", artifact.id),
                Predicates.eq("scope", artifact.scope)
            ))
        }, ExistingConfigQueryAtts::class.java)

        val recordAtts = RecordAtts()
        if (existingConfig != null) {
            recordAtts.setId(existingConfig.ref)
        } else {
            recordAtts.setId(RecordRef.create("config-repo", ""))
        }

        val attributes = ObjectData.create(artifact)
        attributes.remove("id")

        val valueChanged = if (existingConfig == null || artifact.version > existingConfig.version) {
            val valueObj = ObjectData.create()
            valueObj.set(EcosConfigAppConstants.VALUE_SHORT_PROP, attributes.get(PROP_VALUE))
            attributes.set(PROP_VALUE, valueObj)
            true
        } else {
            attributes.set(PROP_VALUE, existingConfig.value)
            false
        }
        attributes.set("configId", artifact.id)

        recordAtts.setAttributes(attributes)

        recordsService.mutate(recordAtts)

        if (valueChanged) {
            zkConfigService.setConfig(ConfigKey(artifact.id, artifact.scope), artifact.value, artifact.version)
        }
    }

    override fun getArtifactType(): String {
        return "app/config"
    }

    override fun listenChanges(listener: Consumer<ConfigDef>) {
    }

    override fun deleteArtifact(artifactId: String) {
        recordsService.delete(RecordRef.create("config", artifactId))
    }

    data class ExistingConfigQueryAtts(
        @AttName("?id")
        val ref: RecordRef,
        val version: Int,
        val value: ObjectData
    )
}

