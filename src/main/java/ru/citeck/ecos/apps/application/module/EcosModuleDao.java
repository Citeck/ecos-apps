package ru.citeck.ecos.apps.application.module;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.domain.EcosAppModuleEntity;
import ru.citeck.ecos.apps.domain.EcosAppModuleRevEntity;
import ru.citeck.ecos.apps.repository.EcosAppModuleRevRepo;
import ru.citeck.ecos.apps.repository.EcosAppModuleRepo;

import java.util.List;

@Service
@Transactional
public class EcosModuleDao {

    private EcosAppModuleRepo modulesRepo;
    private EcosAppModuleRevRepo moduleRevRepo;

    public EcosModuleDao(EcosAppModuleRepo modulesRepo,
                         EcosAppModuleRevRepo moduleRevRepo) {
        this.modulesRepo = modulesRepo;
        this.moduleRevRepo = moduleRevRepo;
    }

    public EcosModuleRev getModuleById(String id) {

        EcosAppModuleEntity moduleById = modulesRepo.getByExtId(id);
        EcosAppModuleRevEntity revision = moduleRevRepo.getLastRevisionByAppId(moduleById.getId());

        EcosModuleImpl module = new EcosModuleImpl();
        module.setId(id);
        module.setRevId(revision.getExtId());
        module.setData(revision.getData());
        module.setKey(revision.getKey());
        module.setName(revision.getName());
        module.setModelVersion(revision.getModelVersion());
        module.setMimetype(revision.getMimetype());
        module.setType(moduleById.getType());

        return module;
    }

    public List<EcosModule> getModulesByAppRev() {

    }
}
