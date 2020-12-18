package ru.citeck.ecos.apps.domain.module.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.module.repo.EcosModuleDepEntity;

import java.util.List;

@Repository
public interface EcosModuleDepRepo extends JpaRepository<EcosModuleDepEntity, Long> {

    @Query("SELECT dep FROM EcosModuleDepEntity dep " +
           "JOIN dep.target targetModule " +
           "WHERE targetModule.id=?1")
    List<EcosModuleDepEntity> getDepsByTarget(long targetModuleId);
}
