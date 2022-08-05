package ru.citeck.ecos.apps.domain.artifact.patch.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository

@Repository
interface ArtifactPatchRepo :
    JpaRepository<ArtifactPatchEntity, Long>,
    JpaSpecificationExecutor<ArtifactPatchEntity> {

    fun findFirstByExtId(extId: String): ArtifactPatchEntity?

    fun findAllByTarget(target: String): List<ArtifactPatchEntity>
}
