package ru.citeck.ecos.apps.domain.artifact.patch.repo

import ru.citeck.ecos.webapp.lib.spring.hibernate.entity.AbstractAuditingEntity
import javax.persistence.*

@Entity
@Table(name = "ecos_artifact_patch")
class ArtifactPatchEntity : AbstractAuditingEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_app_id_gen")
    @SequenceGenerator(name = "ecos_app_id_gen")
    private val id: Long? = null

    override fun getId() = id

    lateinit var extId: String

    var name: String? = null

    lateinit var target: String

    @Column(name = "patch_order")
    var order = 0f

    lateinit var type: String
    lateinit var config: String
}
