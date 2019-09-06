package ru.citeck.ecos.apps.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;

@Repository
public interface EcosAppModuleRevRepo extends JpaRepository<EcosModuleRevEntity, Long> {

    /*@Query(value = "SELECT m FROM EcosAppModule m WHERE m.type=?1 AND m.key = ?2 " +
                   "AND m.version = (SELECT max(m.version) FROM EcosAppModule m WHERE m.key = ?2)")
    EcosAppModuleRevision getLastByKey(String type, String key);*/

    @Query("SELECT m FROM EcosModuleRevEntity m WHERE m.extId = ?1 ORDER BY m.id DESC")
    EcosModuleRevEntity getLastRevisionByAppId(Long id);
}
