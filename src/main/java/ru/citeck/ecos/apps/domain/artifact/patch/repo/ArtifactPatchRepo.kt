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

    fun findAllByEnabledTrueAndTargetAndWorkspace(
        target: String,
        workspace: String
    ): List<ArtifactPatchEntity>

    fun findAllByEnabledTrueAndTargetAndWorkspaceAndSourceTypeIn(
        target: String,
        workspace: String,
        sourceTypes: List<ArtifactSourceType>
    ): List<ArtifactPatchEntity>
}
