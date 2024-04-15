package ru.citeck.ecos.apps.domain.ecosapp.repo

import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity
import ru.citeck.ecos.webapp.lib.spring.hibernate.entity.AbstractAuditingEntity
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "ecos_app")
class EcosAppEntity : AbstractAuditingEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hibernate_sequence")
    @SequenceGenerator(name = "hibernate_sequence")
    private val id: Long? = null

    override fun getId() = id
    lateinit var extId: String

    var name: String? = null
    var version: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artifacts_dir_content_id")
    var artifactsDir: EcosContentEntity? = null

    var artifactsLastModifiedDate: Instant? = null
}
