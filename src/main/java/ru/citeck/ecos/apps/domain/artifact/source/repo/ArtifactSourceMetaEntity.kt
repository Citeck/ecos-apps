package ru.citeck.ecos.apps.domain.artifact.source.repo

import jakarta.persistence.*
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType
import java.time.Instant

@Entity
@Table(name = "ecos_artifact_source_meta")
open class ArtifactSourceMetaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hibernate_sequence")
    @SequenceGenerator(name = "hibernate_sequence")
    open var id: Long? = null

    open lateinit var appName: String
    open lateinit var sourceId: String

    @Enumerated(EnumType.ORDINAL)
    open lateinit var sourceType: ArtifactSourceType
    open lateinit var lastModified: Instant
}
