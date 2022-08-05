package ru.citeck.ecos.apps.domain.artifact.type.repo

import ru.citeck.ecos.webapp.lib.spring.hibernate.entity.AbstractAuditingEntity
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "ecos_artifact_type")
class EcosArtifactTypeEntity : AbstractAuditingEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_artifact_type_id_gen")
    @SequenceGenerator(name = "ecos_artifact_type_id_gen")
    private val id: Long? = null

    override fun getId() = id

    lateinit var extId: String
    lateinit var appName: String

    var internal = false
    var recordsSourceId: String = ""

    lateinit var lastModifiedByApp: Instant

    @OneToOne
    @JoinColumn(name = "last_rev_id")
    var lastRev: EcosArtifactTypeRevEntity? = null
}
