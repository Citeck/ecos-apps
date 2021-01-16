package ru.citeck.ecos.apps.domain.artifact.source.repo

import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "ecos_artifact_source_meta")
open class ArtifactSourceMetaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_artifact_source_meta_id_gen")
    @SequenceGenerator(name = "ecos_artifact_source_meta_id_gen")
    open var id: Long? = null

    open lateinit var sourceId: String
    @Enumerated(EnumType.ORDINAL)
    open lateinit var sourceType: ArtifactSourceType
    open lateinit var lastModified: Instant
}
