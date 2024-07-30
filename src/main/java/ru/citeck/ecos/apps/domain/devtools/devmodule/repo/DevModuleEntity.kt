package ru.citeck.ecos.apps.domain.devtools.devmodule.repo

import jakarta.persistence.*
import ru.citeck.ecos.webapp.lib.spring.hibernate.entity.AbstractAuditingEntity

@Entity
@Table(name = "ecos_dev_module")
class DevModuleEntity : AbstractAuditingEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hibernate_sequence")
    @SequenceGenerator(name = "hibernate_sequence")
    private val id: Long? = null

    @Column(unique = true)
    lateinit var extId: String

    var name: String? = null
    var actions: String? = null

    override fun getId(): Long? = id
}
