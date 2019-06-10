package ru.citeck.ecos.apps.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.EcosAppModule;

@Repository
public interface EcosAppModuleRepository extends JpaRepository<EcosAppModule, Long> {

    @Query(value = "SELECT m FROM EcosAppModule m WHERE m.type=?1 AND m.key = ?2 " +
                   "AND m.version = (SELECT max(m.version) FROM EcosAppModule m WHERE m.key = ?2)")
    EcosAppModule getLastByKey(String type, String key);

}
