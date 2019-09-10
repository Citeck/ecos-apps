package ru.citeck.ecos.apps.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.EcosAppEntity;

import java.util.List;

@Repository
public interface EcosAppRepo extends JpaRepository<EcosAppEntity, Long> {

    @Query("SELECT a FROM EcosAppEntity a WHERE a.extId = ?1")
    EcosAppEntity getByExtId(String extId);

    @Query("SELECT apps FROM EcosAppEntity apps " +
        "JOIN apps.revisions appRev " +
        "JOIN appRev.modules modulesRev " +
        "JOIN modulesRev.module module " +
        "WHERE module.type = ?1 AND module.extId = ?2")
    List<EcosAppEntity> getAppsByModuleId(String moduleType, String moduleExtId);
}
