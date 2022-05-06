package ru.citeck.ecos.apps.domain.config.dto

import ecos.com.fasterxml.jackson210.databind.annotation.JsonDeserialize
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.serialization.annotation.IncludeNonDefault
import ru.citeck.ecos.model.lib.attributes.dto.AttConstraintDef
import ru.citeck.ecos.model.lib.attributes.dto.AttributeType
import ru.citeck.ecos.records2.RecordRef

@IncludeNonDefault
@JsonDeserialize(builder = ConfigValueDef.Builder::class)
data class ConfigValueDef(
    /**
     * Simple config value type
     */
    val type: AttributeType?,
    /**
     * Type configuration. E.g. for ASSOC it may be target type.
     */
    val config: ObjectData,
    /**
     * Value constraint
     */
    val constraint: AttConstraintDef,
    /**
     * Form to edit config value
     */
    val formRef: RecordRef,
    /**
     * Attributes definition for complex config value
     */
    val attributes: List<ConfigValueAttDef>
) {
    companion object {

        @JvmStatic
        val EMPTY = create {}

        @JvmStatic
        fun create(): Builder {
            return Builder()
        }

        @JvmStatic
        fun create(builder: Builder.() -> Unit): ConfigValueDef {
            val builderObj = Builder()
            builder.invoke(builderObj)
            return builderObj.build()
        }
    }

    fun copy(): Builder {
        return Builder(this)
    }

    fun copy(builder: Builder.() -> Unit): ConfigValueDef {
        val builderObj = Builder(this)
        builder.invoke(builderObj)
        return builderObj.build()
    }

    class Builder() {

        var type: AttributeType? = null
        var config: ObjectData = ObjectData.create()
        var constraint: AttConstraintDef = AttConstraintDef.EMPTY
        var formRef: RecordRef = RecordRef.EMPTY
        var attributes: List<ConfigValueAttDef> = emptyList()

        constructor(base: ConfigValueDef) : this() {
            type = base.type
            config = base.config.deepCopy()
            constraint = base.constraint
            formRef = base.formRef
            attributes = base.attributes
        }

        fun withType(type: AttributeType?): Builder {
            this.type = type
            return this
        }

        fun withConfig(config: ObjectData?): Builder {
            this.config = config ?: ObjectData.create()
            return this
        }

        fun withConstraint(constraint: AttConstraintDef?): Builder {
            this.constraint = constraint ?: AttConstraintDef.EMPTY
            return this
        }

        fun withFormRef(formRef: RecordRef?): Builder {
            this.formRef = formRef ?: RecordRef.EMPTY
            return this
        }

        fun withAttributes(attributes: List<ConfigValueAttDef>?): Builder {
            this.attributes = attributes ?: emptyList()
            return this
        }

        fun build(): ConfigValueDef {
            return ConfigValueDef(type, config, constraint, formRef, attributes)
        }
    }
}
