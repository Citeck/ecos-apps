package ru.citeck.ecos.apps.domain.config.api.records

import ru.citeck.ecos.apps.domain.config.service.EcosConfigAppConstants
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.mixin.AttMixin

class ConfigValueMixin : AttMixin {

    companion object {
        const val VALUE_PROP = "_value"
        const val VALUE_PROTECTED_PROP = "_edge.$VALUE_PROP.protected"
        const val PERMISSIONS_WRITE = "permissions._has.Write"
    }

    override fun getAtt(path: String, value: AttValueCtx): Any? {
        return when (path) {
            VALUE_PROP -> {
                val res = value.getAtt("value?json")
                res.get(EcosConfigAppConstants.VALUE_SHORT_PROP)
            }
            VALUE_PROTECTED_PROP -> {
                false
            }
            PERMISSIONS_WRITE -> {
                true
            }
            else -> null
        }
    }

    override fun getProvidedAtts(): Collection<String> {
        return listOf(VALUE_PROP, VALUE_PROTECTED_PROP, PERMISSIONS_WRITE)
    }
}
