package ru.citeck.ecos.apps.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.EcosAppEntity;
import ru.citeck.ecos.apps.domain.EcosModuleTypeEntity;

import java.util.List;

@Repository
public interface EcosModuleTypesRepo extends JpaRepository<EcosAppEntity, Long> {

    //EcosModuleTypeEntity get
}
