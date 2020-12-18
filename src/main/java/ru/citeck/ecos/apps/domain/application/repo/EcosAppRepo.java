package ru.citeck.ecos.apps.domain.application.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.application.repo.EcosAppEntity;

@Repository
public interface EcosAppRepo extends JpaRepository<EcosAppEntity, Long> {

    @Query("SELECT a FROM EcosAppEntity a WHERE a.extId = ?1")
    EcosAppEntity getByExtId(String extId);

}
