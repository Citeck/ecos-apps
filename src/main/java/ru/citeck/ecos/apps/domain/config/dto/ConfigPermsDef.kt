package ru.citeck.ecos.apps.domain.config.dto

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import ru.citeck.ecos.commons.json.serialization.annotation.IncludeNonDefault

@IncludeNonDefault
@JsonDeserialize(builder = ConfigPermsDef.Builder::class)
data class ConfigPermsDef(
    val read: List<String>,
    val write: List<String>
) {
    companion object {

        @JvmStatic
        val EMPTY = create {}

        @JvmStatic
        fun create(): Builder {
            return Builder()
        }

        @JvmStatic
        fun create(builder: Builder.() -> Unit): ConfigPermsDef {
            val builderObj = Builder()
            builder.invoke(builderObj)
            return builderObj.build()
        }
    }

    fun copy(): Builder {
        return Builder(this)
    }

    fun copy(builder: Builder.() -> Unit): ConfigPermsDef {
        val builderObj = Builder(this)
        builder.invoke(builderObj)
        return builderObj.build()
    }

    class Builder() {

        var read: List<String> = emptyList()
        var write: List<String> = emptyList()

        constructor(base: ConfigPermsDef) : this() {
            this.read = base.read
            this.write = base.write
        }

        fun withRead(read: List<String?>?): Builder {
            this.read = read?.filterNotNull()?.filter { it.isNotBlank() } ?: emptyList()
            return this
        }

        fun withWrite(write: List<String?>?): Builder {
            this.write = write?.filterNotNull()?.filter { it.isNotBlank() } ?: emptyList()
            return this
        }

        fun build(): ConfigPermsDef {
            return ConfigPermsDef(read, write)
        }
    }
}
