package ru.citeck.ecos.apps.app.module;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.PublishStatus;
import ru.citeck.ecos.apps.app.content.EcosContentDao;
import ru.citeck.ecos.apps.app.module.event.ModuleRevisionCreated;
import ru.citeck.ecos.apps.app.module.records.EcosModuleRecords;
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

    public int getModulesCount() {
        return (int) moduleRepo.getCount();
    }

    public int getModulesCount(String type) {
        return (int) moduleRepo.getCount(type);
    }

    public List<EcosModuleRevEntity> getModulesLastRev(String type, int skipCount, int maxItems) {
        int page = skipCount / maxItems;
        return moduleRepo.getModulesLastRev(type, PageRequest.of(page, maxItems));
    }

    public List<EcosModuleRevEntity> getAllLastRevisions(int skipCount, int maxItems) {
        int page = skipCount / maxItems;
        return moduleRepo.findAll(PageRequest.of(page, maxItems))
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

        ModuleRef moduleRef = ModuleRef.create(typeId, module.getId());

        EcosModuleEntity moduleEntity = getModule(moduleRef);

        EcosModuleRevEntity lastModuleRev = null;

        if (moduleEntity == null) {

            log.debug("Create new module entity " + moduleRef);

            moduleEntity = new EcosModuleEntity();
            moduleEntity.setExtId(module.getId());
            moduleEntity.setType(typeId);
            moduleEntity.setPublishStatus(PublishStatus.DRAFT);
            moduleEntity = moduleRepo.save(moduleEntity);

        } else {

            lastModuleRev = getLastModuleRev(typeId, module.getId());
        }

        byte[] data = eappsModuleService.writeAsBytes(module);
        EcosContentEntity content = contentDao.upload(data);

        if (lastModuleRev != null) {

            if (Objects.equals(lastModuleRev.getContent(), content)) {

                return lastModuleRev;

            } else if (source != null && !EcosModuleRecords.MODULES_SOURCE.equals(source)) {

                EcosModuleRevEntity lastBySource = getLastModuleRev(moduleRef, source);

                if (lastBySource != null && Objects.equals(lastBySource.getContent(), content)) {
                    return lastModuleRev;
                }
            }
        }

        log.debug("Create new module revision entity " + moduleRef);

        lastModuleRev = new EcosModuleRevEntity();
        lastModuleRev.setSource(source);
        lastModuleRev.setExtId(UUID.randomUUID().toString());
        lastModuleRev.setContent(content);
        lastModuleRev.setModule(moduleEntity);
        lastModuleRev = moduleRevRepo.save(lastModuleRev);

        moduleEntity.setLastRev(lastModuleRev);
        moduleEntity.setPublishStatus(PublishStatus.DRAFT);
        moduleRepo.save(moduleEntity);

        eventPublisher.publishEvent(new ModuleRevisionCreated(typeId, module.getId()));

        return lastModuleRev;
    }

    public EcosModuleRevEntity getLastModuleRev(String type, String id) {
        return getLastModuleRev(ModuleRef.create(type, id));
    }

    public EcosModuleRevEntity getLastModuleRev(ModuleRef moduleRef, String source) {

        Pageable page = PageRequest.of(0, 1);

        List<EcosModuleRevEntity> rev = moduleRevRepo.getModuleRevisions(moduleRef.getType(),
                                                                         moduleRef.getId(),
                                                                         source, page);

        return rev.stream().findFirst().orElse(null);
    }

    public EcosModuleRevEntity getLastModuleRev(ModuleRef moduleRef) {
        return getModule(moduleRef).getLastRev();
    }

    public EcosModuleEntity getModule(ModuleRef ref) {
        return moduleRepo.getByExtId(ref.getType(), ref.getId());
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

    public void delete(ModuleRef ref) {

        EcosModuleEntity module = getModule(ref);

        if (module != null) {
            module.setExtId(module.getExtId() + "_DELETED_" + module.getId());
            module.setDeleted(true);
            moduleRepo.save(module);
        }
    }
}
