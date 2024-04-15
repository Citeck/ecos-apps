package ru.citeck.ecos.apps.domain.artifact.patch.repo

import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType
import ru.citeck.ecos.webapp.lib.spring.hibernate.entity.AbstractAuditingEntity
import javax.persistence.*

@Entity
@Table(name = "ecos_artifact_patch")
class ArtifactPatchEntity : AbstractAuditingEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hibernate_sequence")
    @SequenceGenerator(name = "hibernate_sequence")
    private val id: Long? = null

    override fun getId() = id

    lateinit var extId: String

    var name: String? = null

    lateinit var target: String

    @Column(name = "patch_order")
    var order = 0f

    lateinit var type: String
    lateinit var config: String

    var enabled: Boolean = true

    @Enumerated(EnumType.ORDINAL)
    var sourceType: ArtifactSourceType? = null
}
