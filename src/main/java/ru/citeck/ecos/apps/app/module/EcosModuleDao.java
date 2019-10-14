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
import ru.citeck.ecos.apps.repository.EcosModuleRevRepo;
import ru.citeck.ecos.apps.repository.EcosModuleRepo;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EcosModuleDao {

    private final EcosModuleRepo moduleRepo;
    private final EcosContentDao contentDao;
    private final EcosModuleRevRepo moduleRevRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final EappsModuleService eappsModuleService;

    public EcosModuleDao(EcosModuleRepo moduleRepo,
                         EcosModuleRevRepo moduleRevRepo,
                         EcosContentDao contentDao,
                         ApplicationEventPublisher eventPublisher,
                         EappsModuleService eappsModuleService) {
        this.moduleRepo = moduleRepo;
        this.contentDao = contentDao;
        this.moduleRevRepo = moduleRevRepo;
        this.eventPublisher = eventPublisher;
        this.eappsModuleService = eappsModuleService;
    }

    public List<EcosModuleRevEntity> getAllLastRevisions() {
        return moduleRepo.findAll()
            .stream()
            .map(EcosModuleEntity::getLastRev)
            .collect(Collectors.toList());
    }

    public EcosModuleRevEntity uploadModule(String source, EcosModule module) {

        String typeId = eappsModuleService.getTypeId(module.getClass());
        if (typeId == null) {
            throw new IllegalArgumentException("Unknown module type: "
                                                + module.getClass() + " (" + module.getId() + ")");
        }

        EcosModuleEntity moduleEntity = getModuleByExtId(typeId, module.getId());

        EcosModuleRevEntity lastModuleRev = null;

        if (moduleEntity == null) {

            moduleEntity = new EcosModuleEntity();
            moduleEntity.setExtId(module.getId());
            moduleEntity.setType(typeId);
            moduleEntity = moduleRepo.save(moduleEntity);

        } else {

            lastModuleRev = getLastModuleRev(typeId, module.getId());
        }

        byte[] data = eappsModuleService.writeAsBytes(module);
        EcosContentEntity content = contentDao.upload(data);

        if (lastModuleRev != null) {
            if (Objects.equals(lastModuleRev.getContent(), content)) {
                return lastModuleRev;
            }
        }

        lastModuleRev = new EcosModuleRevEntity();
        lastModuleRev.setSource(source);
        lastModuleRev.setExtId(UUID.randomUUID().toString());
        lastModuleRev.setContent(content);
        lastModuleRev.setModule(moduleEntity);
        lastModuleRev = moduleRevRepo.save(lastModuleRev);

        moduleEntity.setLastRev(lastModuleRev);
        moduleRepo.save(moduleEntity);

        eventPublisher.publishEvent(new ModuleRevisionCreated(typeId, module.getId()));

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
