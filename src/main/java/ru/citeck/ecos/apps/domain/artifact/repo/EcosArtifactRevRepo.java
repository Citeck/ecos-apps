package ru.citeck.ecos.apps.domain.artifact.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EcosArtifactRevRepo extends JpaRepository<EcosArtifactRevEntity, Long> {

    @Query("SELECT m FROM EcosArtifactRevEntity m " +
           "JOIN m.module mm " +
           "WHERE mm.type = ?1 AND mm.extId = ?2 AND mm.deleted = false " +
           "ORDER BY m.id DESC")
    List<EcosArtifactRevEntity> getModuleRevisions(String type, String moduleId, Pageable pageable);

    @Query("SELECT m FROM EcosArtifactRevEntity m " +
           "JOIN m.module mm " +
           "WHERE mm.type = ?1 AND mm.extId = ?2 AND m.source = ?3 AND mm.deleted = false " +
           "ORDER BY m.id DESC")
    List<EcosArtifactRevEntity> getModuleRevisions(String type, String moduleId, String source, Pageable pageable);

    @Query("SELECT m FROM EcosArtifactRevEntity m " +
           "JOIN m.module module " +
           "WHERE m.extId = ?1 AND module.deleted = false")
    EcosArtifactRevEntity getRevByExtId(String extId);
}
