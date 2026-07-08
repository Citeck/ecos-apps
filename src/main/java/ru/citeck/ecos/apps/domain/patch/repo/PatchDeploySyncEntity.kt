package ru.citeck.ecos.apps.domain.patch.repo

import jakarta.persistence.*

/**
 * Single-row watermark that couples artifact deployment with the patch job across nodes and
 * transactions. [deployDate] is bumped inside the deploy transaction on every artifact -> DEPLOYED
 * transition; [patchesSyncDate] is the [deployDate] value the patch job has already reconciled
 * DEPS_WAITING patches against. When the two differ, there was a deploy the patch job has not
 * reacted to yet.
 */
@Entity
@Table(name = "ecos_patch_deploy_sync")
class PatchDeploySyncEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hibernate_sequence")
    @SequenceGenerator(name = "hibernate_sequence")
    var id: Long? = null

    lateinit var syncKey: String

    var deployDate: Long = 0
    var patchesSyncDate: Long = 0
}
