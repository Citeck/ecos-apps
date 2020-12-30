package ru.citeck.ecos.apps.domain.ecosapp.repo

import ru.citeck.ecos.apps.app.audit.repo.AbstractAuditingEntity
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity
import javax.persistence.*

@Entity
@Table(name = "ecos_app_artifact")
open class EcosAppArtifactEntity : AbstractAuditingEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_app_artifact_id_gen")
    @SequenceGenerator(name = "ecos_app_artifact_id_gen")
    private val id: Long? = null

    override fun getId() = id

    open var name: String? = null
    open var extId: String? = null
    open var version: String? = null
    open var typeRefs: String? = null
    open var artifacts: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artifacts_dir_content_id")
    open var artifactsDir: EcosContentEntity? = null
}
