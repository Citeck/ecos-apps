package ru.citeck.ecos.apps.domain.config.dto

import ecos.com.fasterxml.jackson210.databind.annotation.JsonDeserialize
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.serialization.annotation.IncludeNonDefault
import ru.citeck.ecos.model.lib.attributes.dto.AttConstraintDef
import ru.citeck.ecos.model.lib.attributes.dto.AttributeType

@IncludeNonDefault
@JsonDeserialize(builder = ConfigValueAttDef.Builder::class)
data class ConfigValueAttDef(
    val id: String,
    val type: AttributeType,
    val name: MLText,
    val config: ObjectData,
    val multiple: Boolean,
    val mandatory: Boolean,
    val constraint: AttConstraintDef
) {
    companion object {

        @JvmStatic
        val EMPTY = create {}

        @JvmStatic
        fun create(): Builder {
            return Builder()
        }

        @JvmStatic
        fun create(builder: Builder.() -> Unit): ConfigValueAttDef {
            val builderObj = Builder()
            builder.invoke(builderObj)
            return builderObj.build()
        }
    }

    fun copy(): Builder {
        return Builder(this)
    }

    fun copy(builder: Builder.() -> Unit): ConfigValueAttDef {
        val builderObj = Builder(this)
        builder.invoke(builderObj)
        return builderObj.build()
    }

    class Builder() {

        var id: String = ""
        var type: AttributeType = AttributeType.TEXT
        var name: MLText = MLText.EMPTY
        var config: ObjectData = ObjectData.create()
        var multiple: Boolean = false
        var mandatory: Boolean = false
        var constraint: AttConstraintDef = AttConstraintDef.EMPTY

        constructor(base: ConfigValueAttDef) : this() {
            id = base.id
            type = base.type
            name = base.name
            config = base.config
            multiple = base.multiple
            mandatory = base.mandatory
            constraint = base.constraint
        }

        fun withId(id: String?): Builder {
            this.id = id ?: ""
            return this
        }

        fun withType(type: AttributeType?): Builder {
            this.type = type ?: AttributeType.TEXT
            return this
        }

        fun withName(name: MLText?): Builder {
            this.name = name ?: MLText.EMPTY
            return this
        }

        fun withConfig(config: ObjectData?): Builder {
            this.config = config ?: ObjectData.create()
            return this
        }

        fun withMultiple(multiple: Boolean?): Builder {
            this.multiple = multiple ?: false
            return this
        }

        fun withMandatory(mandatory: Boolean?): Builder {
            this.mandatory = mandatory ?: false
            return this
        }

        fun withConstraint(constraint: AttConstraintDef?): Builder {
            this.constraint = constraint ?: AttConstraintDef.EMPTY
            return this
        }

        fun build(): ConfigValueAttDef {
            return ConfigValueAttDef(
                id,
                type,
                name,
                config,
                multiple,
                mandatory,
                constraint,
            )
        }
    }
}
