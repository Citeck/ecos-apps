package ru.citeck.ecos.apps.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;

@Repository
public interface EcosModuleRepo extends JpaRepository<EcosModuleEntity, Long> {

    @Query("SELECT m FROM EcosModuleEntity m WHERE m.extId=?1")
    EcosModuleEntity getByExtId(String extId);

}
