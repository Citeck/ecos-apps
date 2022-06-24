package ru.citeck.ecos.apps.domain.config.api.records

import ru.citeck.ecos.apps.domain.config.service.EcosConfigAppConstants
import ru.citeck.ecos.config.lib.dto.ConfigKey
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.mixin.AttMixin

class ConfigRepoMixin : AttMixin {

    companion object {
        const val VALUE_PROP = "_value"
        const val VALUE_PROTECTED_PROP = "_edge.$VALUE_PROP.protected"
        const val PERMISSIONS_WRITE = "permissions._has.Write"
    }

    override fun getAtt(path: String, value: AttValueCtx): Any? {
        return when (path) {
            VALUE_PROP -> {
                val res = value.getAtt("value?json")
                res[EcosConfigAppConstants.VALUE_SHORT_PROP]
            }
            RecordConstants.ATT_FORM_REF -> {
                val atts = value.getAtts(FormRefAtts::class.java)
                var ref = atts.formRef
                if (RecordRef.isEmpty(atts.formRef)) {
                    val key = ConfigKey.create(atts.scope, atts.configId)
                    ref = RecordRef.create("uiserv", "form", "config$$key")
                }
                ref
            }
            VALUE_PROTECTED_PROP -> false
            PERMISSIONS_WRITE -> true
            else -> null
        }
    }

    override fun getProvidedAtts(): Collection<String> {
        return listOf(VALUE_PROP, VALUE_PROTECTED_PROP, PERMISSIONS_WRITE, RecordConstants.ATT_FORM_REF)
    }

    data class FormRefAtts(
        @AttName("valueDef.formRef?id")
        val formRef: RecordRef,
        val scope: String,
        val configId: String
    )
}
