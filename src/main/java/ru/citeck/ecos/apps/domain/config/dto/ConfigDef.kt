package ru.citeck.ecos.apps.domain.config.dto

import ecos.com.fasterxml.jackson210.databind.annotation.JsonDeserialize
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.json.serialization.annotation.IncludeNonDefault
import ru.citeck.ecos.config.lib.dto.ConfigValueDef

@IncludeNonDefault
@JsonDeserialize(builder = ConfigDef.Builder::class)
data class ConfigDef(
    val id: String,
    val scope: String,
    val name: MLText,
    val value: DataValue,
    val valueDef: ConfigValueDef,
    val version: Int,
    val tags: List<String>,
    val permissions: ConfigPermsDef
) {
    companion object {

        @JvmStatic
        fun create(): Builder {
            return Builder()
        }

        @JvmStatic
        fun create(builder: Builder.() -> Unit): ConfigDef {
            val builderObj = Builder()
            builder.invoke(builderObj)
            return builderObj.build()
        }
    }

    fun copy(): Builder {
        return Builder(this)
    }

    fun copy(builder: Builder.() -> Unit): ConfigDef {
        val builderObj = Builder(this)
        builder.invoke(builderObj)
        return builderObj.build()
    }

    class Builder() {

        var id: String = ""
        var scope: String = ""
        var name: MLText = MLText.EMPTY
        var value: DataValue = DataValue.NULL
        var valueDef: ConfigValueDef = ConfigValueDef.EMPTY
        var version: Int = 0
        var tags: List<String> = emptyList()
        var permissions: ConfigPermsDef = ConfigPermsDef.EMPTY

        constructor(base: ConfigDef) : this() {
            id = base.id
            scope = base.scope
            name = base.name
            value = base.value.copy()
            valueDef = base.valueDef
            version = base.version
            tags = base.tags
            permissions = base.permissions
        }

        fun withId(id: String?): Builder {
            this.id = id ?: ""
            return this
        }

        fun withScope(scope: String?): Builder {
            this.scope = scope ?: ""
            return this
        }

        fun withName(name: MLText?): Builder {
            this.name = name ?: MLText.EMPTY
            return this
        }

        fun withValue(value: DataValue?): Builder {
            this.value = value ?: DataValue.NULL
            return this
        }

        fun withValueDef(valueDef: ConfigValueDef?): Builder {
            this.valueDef = valueDef ?: ConfigValueDef.EMPTY
            return this
        }

        fun withVersion(version: Int?): Builder {
            this.version = version ?: 0
            return this
        }

        fun withTags(tags: List<String?>?): Builder {
            this.tags = tags?.filterNotNull()?.filter { it.isNotBlank() } ?: emptyList()
            return this
        }

        fun withPermissions(permissions: ConfigPermsDef?): Builder {
            this.permissions = permissions ?: ConfigPermsDef.EMPTY
            return this
        }

        fun build(): ConfigDef {
            return ConfigDef(
                id,
                scope,
                name,
                value,
                valueDef,
                version,
                tags,
                permissions
            )
        }
    }
}
