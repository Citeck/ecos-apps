package ru.citeck.ecos.apps.domain.module.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.module.repo.EcosModuleEntity;
import ru.citeck.ecos.apps.domain.module.repo.EcosModuleRevEntity;

import java.util.List;

@Repository
public interface EcosModuleRepo extends JpaRepository<EcosModuleEntity, Long> {

    @Query("SELECT m FROM EcosModuleEntity m " +
           "WHERE m.type = ?1 AND m.extId = ?2 AND m.deleted = false")
    EcosModuleEntity getByExtId(String type, String extId);

    @Query("SELECT m FROM EcosModuleEntity m " +
        "WHERE m.type = ?1 AND m.key = ?2 AND m.deleted = false")
    EcosModuleEntity findByTypeAndKey(String type, String key);

    @Query("SELECT m FROM EcosModuleEntity m " +
           "WHERE m.type = ?1 AND m.deleted = false")
    List<EcosModuleEntity> findAllByType(String type);

    @Query("SELECT rev FROM EcosModuleEntity module " +
           "JOIN module.lastRev rev " +
           "WHERE module.type = ?1 AND module.deleted = false " +
           "ORDER BY module.id DESC")
    List<EcosModuleRevEntity> getModulesLastRev(String type, Pageable pageable);

    @Query("SELECT COUNT(module) FROM EcosModuleEntity module " +
           "WHERE module.type = ?1 AND module.deleted = false")
    long getCount(String type);

    @Query("SELECT COUNT(module) FROM EcosModuleEntity module " +
           "WHERE module.deleted = false")
    long getCount();
}
