package ru.citeck.ecos.apps.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.EcosAppRevEntity;

@Repository
public interface EcosAppRevRepo extends JpaRepository<EcosAppRevEntity, Long> {

    //@Query(value = "SELECT a FROM EcosApplication a WHERE a.key = ?1 " +
                   //"AND a.version = (SELECT max(a.version) FROM EcosApplication a WHERE a.key = ?1)")
    //EcosApplicationRevEntity getLastByKey(String key);
}
