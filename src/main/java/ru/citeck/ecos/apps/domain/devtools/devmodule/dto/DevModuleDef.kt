package ru.citeck.ecos.apps.domain.devtools.devmodule.dto

import ecos.com.fasterxml.jackson210.databind.annotation.JsonDeserialize
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.json.serialization.annotation.IncludeNonDefault
import ru.citeck.ecos.records2.RecordRef

@IncludeNonDefault
@JsonDeserialize(builder = DevModuleDef.Builder::class)
data class DevModuleDef(
    val id: String,
    val name: MLText,
    val actions: List<RecordRef>
) {

    companion object {
        @JvmField
        val EMPTY = create {}

        fun create(): Builder {
            return Builder()
        }

        fun create(builder: Builder.() -> Unit): DevModuleDef {
            val builderObj = Builder()
            builder.invoke(builderObj)
            return builderObj.build()
        }
    }

    class Builder() {

        var id: String = ""
        var name: MLText = MLText.EMPTY
        var actions: List<RecordRef> = emptyList()

        constructor(base: DevModuleDef) : this() {
            this.id = base.id
            this.name = base.name
            this.actions = base.actions
        }

        fun withId(id: String?): Builder {
            this.id = id ?: ""
            return this
        }

        fun withName(name: MLText?): Builder {
            this.name = name ?: MLText.EMPTY
            return this
        }

        fun withActions(actions: List<RecordRef>?): Builder {
            this.actions = actions ?: emptyList()
            return this
        }

        fun build(): DevModuleDef {
            return DevModuleDef(id, name, actions)
        }
    }
}
