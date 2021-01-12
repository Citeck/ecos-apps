package ru.citeck.ecos.apps.domain.artifact.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactSourceType;

import java.util.List;

@Repository
public interface EcosArtifactsRevRepo extends JpaRepository<EcosArtifactRevEntity, Long> {

    @Query("SELECT rev FROM EcosArtifactRevEntity rev " +
           "JOIN rev.module module " +
           "WHERE module.type = ?1 AND module.extId = ?2 AND module.deleted = false " +
           "ORDER BY rev.createdDate DESC")
    List<EcosArtifactRevEntity> getArtifactRevisions(String type,
                                                     String artifactId,
                                                     Pageable pageable);

    @Query("SELECT rev FROM EcosArtifactRevEntity rev " +
           "JOIN rev.module module " +
           "WHERE module.type = ?1 AND module.extId = ?2 " +
                "AND rev.sourceType = ?3 " +
                "AND module.deleted = false " +
           "ORDER BY rev.createdDate DESC")
    List<EcosArtifactRevEntity> getArtifactRevisions(String type,
                                                     String artifactId,
                                                     ArtifactSourceType sourceType,
                                                     Pageable pageable);

    @Query("SELECT m FROM EcosArtifactRevEntity m " +
           "JOIN m.module module " +
           "WHERE m.extId = ?1 AND module.deleted = false")
    EcosArtifactRevEntity getRevByExtId(String extId);
}
