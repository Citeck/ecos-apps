package ru.citeck.ecos.apps.domain.artifact.type.repo

import jakarta.persistence.*
import ru.citeck.ecos.apps.domain.common.repo.AbstractImmutableEntity
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity

@Entity
@Table(name = "ecos_artifact_type_rev")
open class EcosArtifactTypeRevEntity : AbstractImmutableEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hibernate_sequence")
    @SequenceGenerator(name = "hibernate_sequence")
    private val id: Long? = null

    override fun getId() = id

    @ManyToOne
    @JoinColumn(name = "artifact_type_id")
    open lateinit var artifactType: EcosArtifactTypeEntity

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prev_rev_id")
    open var prevRev: EcosArtifactTypeRevEntity? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id")
    open lateinit var content: EcosContentEntity

    open lateinit var modelVersion: String
}
