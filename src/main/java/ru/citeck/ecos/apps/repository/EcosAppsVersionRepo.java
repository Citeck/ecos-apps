package ru.citeck.ecos.apps.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.EcosApplicationRevision;

@Repository
public interface EcosAppsVersionRepo extends JpaRepository<EcosApplicationRevision, Long> {

    @Query(value = "SELECT a FROM EcosApplication a WHERE a.key = ?1 " +
                   "AND a.version = (SELECT max(a.version) FROM EcosApplication a WHERE a.key = ?1)")
    EcosApplicationRevision getLastByKey(String key);
}
