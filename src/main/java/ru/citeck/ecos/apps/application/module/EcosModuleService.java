package ru.citeck.ecos.apps.application.module;

import org.springframework.stereotype.Service;
import ru.citeck.ecos.apps.domain.EcosAppModuleRevision;
import ru.citeck.ecos.apps.repository.EcosAppModulesRepo;

@Service
public class EcosModuleService {

    private EcosAppModulesRepo modulesRepo;
    private EcosAppModuleRevision modulesVersionRepo;

    public EcosModuleService(EcosAppModulesRepo modulesRepo,
                             EcosAppModuleRevision modulesVersionRepo) {
        this.modulesRepo = modulesRepo;
        this.modulesVersionRepo = modulesVersionRepo;
    }


}
