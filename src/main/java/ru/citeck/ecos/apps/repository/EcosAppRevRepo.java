package ru.citeck.ecos.apps.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.app.DeployStatus;
import ru.citeck.ecos.apps.domain.EcosAppRevEntity;

import java.util.List;

@Repository
public interface EcosAppRevRepo extends JpaRepository<EcosAppRevEntity, Long> {

    @Query("SELECT a FROM EcosAppRevEntity a WHERE a.extId = ?1")
    EcosAppRevEntity getByExtId(String extId);

    @Query("SELECT a FROM EcosAppRevEntity a JOIN a.modules m WHERE m.extId = ?2")
    List<EcosAppRevEntity> getAppsByModuleRev(DeployStatus status, String revId, Pageable pageable);

    @Query("SELECT a FROM EcosAppRevEntity a JOIN a.application aa WHERE aa.extId = ?1 ORDER BY a.id DESC")
    List<EcosAppRevEntity> getAppRevisions(String appExtId, Pageable pageable);

    @Query("SELECT a FROM EcosAppRevEntity a WHERE a.application.id = ?1 ORDER BY a.id DESC")
    List<EcosAppRevEntity> getAppRevisions(long appId, Pageable pageable);
}
