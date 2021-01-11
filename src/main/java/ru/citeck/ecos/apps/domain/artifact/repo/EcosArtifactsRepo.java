package ru.citeck.ecos.apps.domain.artifact.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.ecosapp.repo.EcosAppArtifactEntity;

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

    @Query("SELECT rev FROM EcosArtifactEntity module " +
           "JOIN module.lastRev rev " +
           "WHERE module.type = ?1 AND module.deleted = false " +
           "ORDER BY module.id DESC")
    List<EcosArtifactRevEntity> getModulesLastRev(String type, Pageable pageable);

    @Query("SELECT COUNT(module) FROM EcosArtifactEntity module " +
           "WHERE module.type = ?1 AND module.deleted = false")
    long getCount(String type);

    @Query("SELECT COUNT(module) FROM EcosArtifactEntity module " +
           "WHERE module.deleted = false")
    long getCount();
}
