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

    @Query("SELECT m FROM EcosArtifactEntity m " +
           "WHERE m.type = ?1 AND m.extId = ?2 AND m.deleted = false")
    EcosArtifactEntity getByExtId(String type, String extId);

    @Query("SELECT m FROM EcosArtifactEntity m " +
           "WHERE m.type = ?1 AND m.deleted = false")
    List<EcosArtifactEntity> findAllByType(String type);

    @Query("SELECT m FROM EcosArtifactEntity m " +
        "WHERE m.type = ?1 AND m.deployStatus = ?2 AND m.deleted = false")
    List<EcosArtifactEntity> findAllByTypeAndDeployStatus(String type, DeployStatus status);

    @Query("SELECT m FROM EcosArtifactEntity m " +
        "WHERE m.deployStatus = ?1 AND m.deployRetryCounter <= ?2 AND m.lastModifiedDate < ?3 AND m.deleted = false")
    List<EcosArtifactEntity> findArtifactsToRetry(DeployStatus status, int retryCounter, Instant changedBefore);

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
}
