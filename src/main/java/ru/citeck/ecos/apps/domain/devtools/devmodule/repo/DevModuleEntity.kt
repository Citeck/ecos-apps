package ru.citeck.ecos.apps.domain.devtools.devmodule.repo

import ru.citeck.ecos.apps.app.audit.repo.AbstractAuditingEntity
import javax.persistence.*

@Entity
@Table(name = "ecos_dev_module")
class DevModuleEntity : AbstractAuditingEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_dev_module_seq_gen")
    @SequenceGenerator(name = "ecos_dev_module_seq_gen")
    private val id: Long? = null

    @Column(unique = true)
    lateinit var extId: String

    var name: String? = null
    var actions: String? = null

    override fun getId(): Long? = id
}
