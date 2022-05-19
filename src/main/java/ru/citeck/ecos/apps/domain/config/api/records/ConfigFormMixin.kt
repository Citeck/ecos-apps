package ru.citeck.ecos.apps.domain.config.api.records

import ru.citeck.ecos.apps.domain.config.service.EcosConfigAppConstants
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.mixin.AttMixin

class ConfigFormMixin : AttMixin {

    override fun getAtt(path: String, value: AttValueCtx): Any? {
        return when (path) {
            RecordConstants.ATT_FORM_REF -> {
                val localId = value.getLocalId()
                if (localId.isNotBlank()) {
                    return RecordRef.create("uiserv", "form", "rec\$eapps/config@$localId")
                } else {
                    null
                }
            }
            "_formDef" -> {
                val attJsonValue = value.getAtt("value?json")
                val attValue = attJsonValue.get(EcosConfigAppConstants.VALUE_SHORT_PROP)
                if (attValue.isTextual()) {
                    listOf(
                        ObjectData.create(
                            """
                        {
                            "type": "text",
                            "key": "_value"
                        }
                            """.trimIndent()
                        )
                    )
                } else {
                    listOf(
                        ObjectData.create(
                            """
                        {
                            "type": "text",
                            "key": "_value"
                        }
                            """.trimIndent()
                        )
                    )
                }
            }
            else -> error("Unknown path: $path")
        }
    }

    override fun getProvidedAtts(): Collection<String> {
        return listOf(RecordConstants.ATT_FORM_REF, "_formDef")
    }
}
