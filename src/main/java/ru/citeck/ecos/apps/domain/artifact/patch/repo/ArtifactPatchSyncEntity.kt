package ru.citeck.ecos.apps.domain.artifact.patch.repo

import javax.persistence.*

@Entity
@Table(name = "ecos_artifact_patch_sync")
class ArtifactPatchSyncEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hibernate_sequence")
    @SequenceGenerator(name = "hibernate_sequence")
    var id: Long? = null

    lateinit var artifactType: String
    lateinit var artifactExtId: String

    var patchLastModified: Long = 0
    var artifactLastModified: Long = 0
}
