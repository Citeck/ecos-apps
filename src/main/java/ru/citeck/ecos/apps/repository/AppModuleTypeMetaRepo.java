package ru.citeck.ecos.apps.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.AppModuleTypeMetaEntity;
import ru.citeck.ecos.apps.domain.EcosAppEntity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface AppModuleTypeMetaRepo extends JpaRepository<AppModuleTypeMetaEntity, Long> {

    Optional<AppModuleTypeMetaEntity> findByAppAndModuleType(EcosAppEntity app, String type);

    List<AppModuleTypeMetaEntity> findAllByApp(EcosAppEntity app);

    List<AppModuleTypeMetaEntity> findAllByAppAndModuleTypeIn(EcosAppEntity app, Set<String> types);
}
