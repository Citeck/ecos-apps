package ru.citeck.ecos.apps.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.EcosContentEntity;
import ru.citeck.ecos.apps.domain.EcosModuleTypesEntity;

import java.util.List;

@Repository
public interface EcosModuleTypesRepo extends JpaRepository<EcosModuleTypesEntity, Long> {

    EcosModuleTypesEntity findBySource(String source);

    EcosModuleTypesEntity findBySourceAndContent(String source, EcosContentEntity content);

    EcosModuleTypesEntity findFirstBySourceOrderByCreatedDateDesc(String source);

    @Query("SELECT DISTINCT t.source FROM EcosModuleTypesEntity t")
    List<String> getSources();
}
