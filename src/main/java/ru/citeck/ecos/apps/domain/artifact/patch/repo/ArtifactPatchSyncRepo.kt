package ru.citeck.ecos.apps.domain.artifact.patch.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ArtifactPatchSyncRepo : JpaRepository<ArtifactPatchSyncEntity, Long>,
    JpaSpecificationExecutor<ArtifactPatchSyncEntity> {

    @Query("SELECT sync FROM ArtifactPatchSyncEntity sync " +
        "WHERE sync.artifactLastModified <> sync.patchLastModified")
    fun findOutOfSyncArtifacts(): List<ArtifactPatchSyncEntity>

    @Query("SELECT sync FROM ArtifactPatchSyncEntity sync " +
        "WHERE sync.artifactType=?1 AND sync.artifactExtId=?2")
    fun findByArtifact(type: String, extId: String): ArtifactPatchSyncEntity?
}
