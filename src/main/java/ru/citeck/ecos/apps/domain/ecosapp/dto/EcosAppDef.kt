package ru.citeck.ecos.apps.domain.ecosapp.dto

import ecos.com.fasterxml.jackson210.databind.annotation.JsonDeserialize
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.Version
import ru.citeck.ecos.webapp.api.entity.EntityRef
import com.fasterxml.jackson.databind.annotation.JsonDeserialize as JackDeserialize

@JsonDeserialize(builder = EcosAppDef.Builder::class)
@JackDeserialize(builder = EcosAppDef.Builder::class)
data class EcosAppDef(
    val id: String,
    val name: MLText,
    val version: Version,
    val typeRefs: List<EntityRef>,
    val artifacts: List<EntityRef>
) {

    companion object {

        @JvmStatic
        fun create(): Builder {
            return Builder()
        }

        @JvmStatic
        fun create(builder: Builder.() -> Unit): EcosAppDef {
            val builderObj = Builder()
            builder.invoke(builderObj)
            return builderObj.build()
        }
    }

    fun copy(): Builder {
        return Builder(this)
    }

    fun copy(builder: Builder.() -> Unit): EcosAppDef {
        val builderObj = Builder(this)
        builder.invoke(builderObj)
        return builderObj.build()
    }

    open class Builder() {

        var id: String = ""
        var name: MLText = MLText()
        var version: Version = Version.valueOf("1.0")
        var typeRefs: List<EntityRef> = emptyList()
        var artifacts: List<EntityRef> = emptyList()

        constructor(base: EcosAppDef) : this() {
            id = base.id
            name = base.name
            version = base.version
            typeRefs = DataValue.create(base.typeRefs).asList(EntityRef::class.java)
            artifacts = DataValue.create(base.artifacts).asList(EntityRef::class.java)
        }

        fun withId(id: String): Builder {
            this.id = id
            return this
        }

        fun withName(name: MLText): Builder {
            this.name = name
            return this
        }

        fun withVersion(version: Version): Builder {
            this.version = version
            return this
        }

        fun withTypeRefs(typeRefs: List<EntityRef>): Builder {
            this.typeRefs = typeRefs
            return this
        }

        fun withArtifacts(artifacts: List<EntityRef>): Builder {
            this.artifacts = artifacts
            return this
        }

        fun build(): EcosAppDef {
            return EcosAppDef(
                id,
                name,
                version,
                typeRefs,
                artifacts
            )
        }
    }
}
