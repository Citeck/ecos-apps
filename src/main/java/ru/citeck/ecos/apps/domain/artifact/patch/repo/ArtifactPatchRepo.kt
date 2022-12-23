package ru.citeck.ecos.apps.domain.artifact.patch.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType

@Repository
interface ArtifactPatchRepo :
    JpaRepository<ArtifactPatchEntity, Long>,
    JpaSpecificationExecutor<ArtifactPatchEntity> {

    fun findFirstByExtId(extId: String): ArtifactPatchEntity?

    fun findAllByEnabledTrueAndTarget(target: String): List<ArtifactPatchEntity>

    fun findAllByEnabledTrueAndTargetAndSourceTypeIn(target: String, sourceTypes: List<ArtifactSourceType>): List<ArtifactPatchEntity>
}
