package ru.citeck.ecos.apps.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.EcosAppRevDepEntity;

import java.util.List;

@Repository
public interface EcosAppRevDepRepo extends JpaRepository<EcosAppRevDepEntity, Long> {

    @Query("SELECT dep FROM EcosAppRevDepEntity dep " +
           "JOIN dep.target targetApp " +
           "WHERE targetApp.id=?1")
    List<EcosAppRevDepEntity> getDepsByTarget(long targetAppId);
}
