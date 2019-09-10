package ru.citeck.ecos.apps.app.module;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;
import ru.citeck.ecos.apps.repository.EcosModuleRevRepo;
import ru.citeck.ecos.apps.repository.EcosModuleRepo;

import java.util.List;

@Slf4j
@Component
public class EcosModuleDao {

    private EcosModuleRepo moduleRepo;
    private EcosModuleRevRepo moduleRevRepo;

    public EcosModuleDao(EcosModuleRepo moduleRepo,
                         EcosModuleRevRepo moduleRevRepo) {
        this.moduleRepo = moduleRepo;
        this.moduleRevRepo = moduleRevRepo;
    }

    public EcosModuleRevEntity getLastModuleRev(String type, String id) {
        Pageable page = PageRequest.of(0, 1);
        List<EcosModuleRevEntity> result = moduleRevRepo.getModuleRevisions(type, id, page);
        return result.stream().findFirst().orElse(null);
    }

    public EcosModuleEntity getModuleByExtId(String type, String extId) {
        return moduleRepo.getByExtId(type, extId);
    }

    public EcosModuleRevEntity getModuleRev(String revId) {
        return moduleRevRepo.getRevByExtId(revId);
    }

    public EcosModuleEntity save(EcosModuleEntity entity) {
        return moduleRepo.save(entity);
    }

    public EcosModuleRevEntity save(EcosModuleRevEntity entity) {
        return moduleRevRepo.save(entity);
    }
}
