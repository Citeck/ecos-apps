package ru.citeck.ecos.apps.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.EcosApplicationEntity;

@Repository
public interface EcosAppsRepo extends JpaRepository<EcosApplicationEntity, Long> {

    @Query(value = "SELECT a FROM EcosApplicationEntity a WHERE a.key = ?1 " +
                   "AND a.version = (SELECT max(a.version) FROM EcosApplicationEntity a WHERE a.key = ?1)")
    EcosApplicationEntity getLastByKey(String key);
}
