package ru.citeck.ecos.apps.app.module;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.content.EcosContentDao;
import ru.citeck.ecos.apps.app.module.event.ModuleRevisionCreated;
import ru.citeck.ecos.apps.domain.EcosContentEntity;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;
import ru.citeck.ecos.apps.module.type.EcosModule;
import ru.citeck.ecos.apps.repository.EcosModuleRevRepo;
import ru.citeck.ecos.apps.repository.EcosModuleRepo;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
public class EcosModuleDao {

    private final EcosModuleRepo moduleRepo;
    private final EcosContentDao contentDao;
    private final EcosModuleRevRepo moduleRevRepo;
    private final ApplicationEventPublisher eventPublisher;

    public EcosModuleDao(EcosModuleRepo moduleRepo,
                         EcosModuleRevRepo moduleRevRepo,
                         EcosContentDao contentDao,
                         ApplicationEventPublisher eventPublisher) {
        this.moduleRepo = moduleRepo;
        this.contentDao = contentDao;
        this.moduleRevRepo = moduleRevRepo;
        this.eventPublisher = eventPublisher;
    }

    public EcosModuleRevEntity uploadModule(String source, EcosModule module) {

        EcosModuleEntity moduleEntity = getModuleByExtId(module.getType(), module.getId());

        EcosModuleRevEntity lastModuleRev = null;

        if (moduleEntity == null) {

            moduleEntity = new EcosModuleEntity();
            moduleEntity.setExtId(module.getId());
            moduleEntity.setType(module.getType());
            moduleEntity = moduleRepo.save(moduleEntity);

        } else {

            lastModuleRev = getLastModuleRev(module.getType(), module.getId());
        }

        EcosContentEntity content = contentDao.upload(module.getData());

        if (lastModuleRev != null) {

            if (Objects.equals(lastModuleRev.getContent(), content)
                && Objects.equals(lastModuleRev.getName(), module.getName())) {
                return lastModuleRev;
            }
        }

        lastModuleRev = new EcosModuleRevEntity();
        lastModuleRev.setSource(source);
        lastModuleRev.setDataType(module.getDataType());
        lastModuleRev.setExtId(UUID.randomUUID().toString());
        lastModuleRev.setModelVersion(module.getModelVersion());
        lastModuleRev.setName(module.getName());
        lastModuleRev.setContent(content);
        lastModuleRev.setModule(moduleEntity);
        lastModuleRev = moduleRevRepo.save(lastModuleRev);

        eventPublisher.publishEvent(new ModuleRevisionCreated(module.getType(), module.getId()));

        return lastModuleRev;
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
