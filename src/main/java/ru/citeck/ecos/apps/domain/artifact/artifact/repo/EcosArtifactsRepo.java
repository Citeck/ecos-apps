package ru.citeck.ecos.apps.domain.artifact.artifact.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.DeployStatus;

import java.time.Instant;
import java.util.List;

@Repository
public interface EcosArtifactsRepo extends JpaRepository<EcosArtifactEntity, Long>,
                                        JpaSpecificationExecutor<EcosArtifactEntity> {

    List<EcosArtifactEntity> findAllByEcosApp(String ecosApp);

    List<EcosArtifactEntity> findAllByEcosAppAndWorkspace(String ecosApp, String workspace);

    @Query("SELECT m FROM EcosArtifactEntity m " +
           "WHERE m.type = ?1 AND m.extId = ?2 AND m.workspace = ?3 AND m.deleted = false")
    EcosArtifactEntity getByExtId(String type, String extId, String workspace);

    /**
     * Looks up a placeholder row — same {@code (type, extId)} but no committed revision yet.
     * Such rows are created by {@code setEcosAppFull} / {@code getDepsEntities} when an ecos-app
     * references an artifact whose actual content hasn't been uploaded yet. The first real upload
     * of that artifact "claims" the placeholder (re-homing it into the resolved workspace) instead
     * of creating a duplicate row alongside.
     * <p>
     * Returns the lowest-id match if multiple coexist (placeholders in different workspaces for the
     * same {@code (type, extId)} are theoretically allowed by the unique key, though uncommon).
     */
    EcosArtifactEntity findFirstByTypeAndExtIdAndLastRevIsNullAndDeletedFalseOrderByIdAsc(String type, String extId);

    /**
     * True when this app already owns an artifact with the given {@code (type, extId)} in any
     * workspace. Used by {@code setEcosAppFull} on re-deploy to avoid creating a stray placeholder
     * at the ref's workspace when the real artifact already lives somewhere else (because
     * {@code resolveUploadWorkspace} re-homed it via the content-workspace fallback).
     */
    boolean existsByTypeAndExtIdAndEcosAppAndDeletedFalse(String type, String extId, String ecosApp);

    @Query("SELECT m FROM EcosArtifactEntity m " +
           "WHERE m.type = ?1 AND m.deleted = false")
    List<EcosArtifactEntity> findAllByType(String type);

    @Query("SELECT m FROM EcosArtifactEntity m " +
        "WHERE m.type = ?1 AND m.deployStatus = ?2 AND m.deleted = false AND m.lastModifiedDate <= ?3")
    List<EcosArtifactEntity> findAllByTypeAndDeployStatus(String type,
                                                          DeployStatus status,
                                                          Instant lastModified,
                                                          Pageable page);

    @Query("SELECT m FROM EcosArtifactEntity m " +
        "WHERE m.deployStatus = ?1 AND m.deployRetryCounter <= ?2 AND m.lastModifiedDate < ?3 AND m.deleted = false")
    List<EcosArtifactEntity> findArtifactsToRetry(DeployStatus status, int retryCounter, Instant changedBefore);

    long countByDeployStatusAndDeletedFalseAndLastModifiedDateLessThanEqual(
        DeployStatus status, Instant since
    );

    @Query("SELECT rev FROM EcosArtifactEntity module " +
           "JOIN module.lastRev rev " +
           "WHERE module.type = ?1 AND module.deleted = false " +
           "ORDER BY module.id DESC")
    List<EcosArtifactRevEntity> getArtifactsLastRev(String type, Pageable pageable);

    @Query("SELECT max(artifact.lastModifiedDate) FROM EcosArtifactEntity artifact")
    Instant getLastModifiedTime();

    @Query("SELECT COUNT(artifact) FROM EcosArtifactEntity artifact " +
           "WHERE artifact.type = ?1 AND artifact.deleted = false")
    long getCount(String type);

    @Query("SELECT COUNT(artifact) FROM EcosArtifactEntity artifact " +
           "WHERE artifact.deleted = false")
    long getCount();

    @Query("SELECT artifact FROM EcosArtifactEntity artifact " +
        "WHERE artifact.ecosApp = ?1 AND artifact.deleted = false")
    List<EcosArtifactEntity> getArtifactsByEcosApp(String ecosApp);

    @Query("SELECT artifact FROM EcosArtifactEntity artifact " +
        "WHERE artifact.ecosApp = ?1 AND artifact.workspace = ?2 AND artifact.deleted = false")
    List<EcosArtifactEntity> getArtifactsByEcosAppAndWorkspace(String ecosApp, String workspace);
}
