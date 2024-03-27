package ru.citeck.ecos.apps.domain.artifact.artifact.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.ArtifactRevSourceType;

import java.time.Instant;
import java.util.List;

@Repository
public interface EcosArtifactsRevRepo extends JpaRepository<EcosArtifactRevEntity, Long> {

    @Query("SELECT rev FROM EcosArtifactRevEntity rev " +
           "JOIN rev.artifact module " +
           "WHERE module.type = ?1 AND module.extId = ?2 AND module.deleted = false " +
           "ORDER BY rev.createdDate DESC")
    List<EcosArtifactRevEntity> getArtifactRevisions(String type,
                                                     String artifactId,
                                                     Pageable pageable);

    @Query("SELECT rev FROM EcosArtifactRevEntity rev " +
           "JOIN rev.artifact module " +
           "WHERE module.type = ?1 AND module.extId = ?2 AND module.deleted = false AND rev.createdDate > ?3 " +
           "ORDER BY rev.createdDate DESC")
    List<EcosArtifactRevEntity> getArtifactRevisionsSince(String type,
                                                          String artifactId,
                                                          Instant since,
                                                          Pageable pageable);

    @Query("SELECT rev FROM EcosArtifactRevEntity rev " +
           "JOIN rev.artifact module " +
           "WHERE module.type = ?1 AND module.extId = ?2 " +
                "AND rev.sourceType in ?3 " +
                "AND module.deleted = false " +
           "ORDER BY rev.createdDate DESC")
    List<EcosArtifactRevEntity> getArtifactRevisions(String type,
                                                     String artifactId,
                                                     List<ArtifactRevSourceType> sourceType,
                                                     Pageable pageable);

    @Query("SELECT m FROM EcosArtifactRevEntity m " +
           "JOIN m.artifact module " +
           "WHERE m.extId = ?1 AND module.deleted = false")
    EcosArtifactRevEntity getRevByExtId(String extId);
}
