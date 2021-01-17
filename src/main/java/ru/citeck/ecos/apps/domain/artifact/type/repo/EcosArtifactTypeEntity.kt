package ru.citeck.ecos.apps.domain.artifact.type.repo

import ru.citeck.ecos.apps.app.audit.repo.AbstractAuditingEntity
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "ecos_artifact_type")
open class EcosArtifactTypeEntity : AbstractAuditingEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_artifact_type_id_gen")
    @SequenceGenerator(name = "ecos_artifact_type_id_gen")
    private val id: Long? = null

    override fun getId() = id

    open lateinit var extId: String
    open lateinit var appName: String

    open var internal = false
    open var recordsSourceId: String = ""

    open lateinit var lastModifiedByApp: Instant

    @OneToOne
    @JoinColumn(name = "last_rev_id")
    open var lastRev: EcosArtifactTypeRevEntity? = null
}
