package ru.citeck.ecos.apps.domain.devtools.buildinfo.dto

import ecos.com.fasterxml.jackson210.databind.annotation.JsonDeserialize
import mu.KotlinLogging
import com.fasterxml.jackson.databind.annotation.JsonDeserialize as JackJsonDeserialize

import ru.citeck.ecos.commons.data.ObjectData

@JsonDeserialize(builder = BuildInfo.Builder::class)
@JackJsonDeserialize(builder = BuildInfo.Builder::class)
data class BuildInfo(
    val id: String,
    val label: String,
    val description: String,
    val info: ObjectData
) {
    companion object {

        val log = KotlinLogging.logger {}

        @JvmStatic
        fun create(): Builder {
            return Builder()
        }

        @JvmStatic
        fun create(builder: Builder.() -> Unit): BuildInfo {
            val builderObj = Builder()
            builder.invoke(builderObj)
            return builderObj.build()
        }
    }

    fun copy(): Builder {
        return Builder(this)
    }

    fun copy(builder: Builder.() -> Unit): BuildInfo {
        val builderObj = Builder(this)
        builder.invoke(builderObj)
        return builderObj.build()
    }

    class Builder() {

        var id: String = ""
        var label: String = ""
        var description: String = ""
        var info: ObjectData = ObjectData.create()

        constructor(base: BuildInfo) : this() {
            id = base.id
            label = base.label
            description = base.description
            info = base.info.deepCopy()
        }

        fun withId(id: String): Builder {
            this.id = id
            return this
        }

        fun withLabel(label: String): Builder {
            this.label = label
            return this
        }

        fun withDescription(description: String): Builder {
            this.description = description
            return this
        }

        fun withInfo(info: ObjectData): Builder {
            this.info = info.deepCopy()
            return this
        }

        fun build(): BuildInfo {
            return BuildInfo(id, label, description, info)
        }
    }
}
