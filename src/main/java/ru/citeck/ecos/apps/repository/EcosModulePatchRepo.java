package ru.citeck.ecos.apps.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.EcosModulePatchEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface EcosModulePatchRepo extends JpaRepository<EcosModulePatchEntity, Long>,
                                             JpaSpecificationExecutor<EcosModulePatchEntity> {

    Optional<EcosModulePatchEntity> findFirstByExtId(String extId);

    List<EcosModulePatchEntity> findAllByTarget(String target);
}
