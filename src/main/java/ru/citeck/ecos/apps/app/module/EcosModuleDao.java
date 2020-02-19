package ru.citeck.ecos.apps.app.module;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.PublishStatus;
import ru.citeck.ecos.apps.app.UploadStatus;
import ru.citeck.ecos.apps.app.content.EcosContentDao;
import ru.citeck.ecos.apps.app.module.records.EcosModuleRecords;
import ru.citeck.ecos.apps.domain.EcosContentEntity;
import ru.citeck.ecos.apps.domain.EcosModuleDepEntity;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;
import ru.citeck.ecos.apps.repository.EcosModuleDepRepo;
import ru.citeck.ecos.apps.repository.EcosModuleRevRepo;
import ru.citeck.ecos.apps.repository.EcosModuleRepo;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EcosModuleDao {

    private final EcosModuleRepo moduleRepo;
    private final EcosContentDao contentDao;
    private final EcosModuleRevRepo moduleRevRepo;
    private final EappsModuleService eappsModuleService;
    private final EcosModuleDepRepo moduleDepRepo;

    public int getModulesCount() {
        return (int) moduleRepo.getCount();
    }

    public int getModulesCount(String type) {
        return (int) moduleRepo.getCount(type);
    }

    public List<EcosModuleEntity> getAllModules() {
        return moduleRepo.findAll();
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

    public UploadStatus<EcosModuleRevEntity> uploadModule(String source, EcosModule module) {

        String typeId = eappsModuleService.getTypeId(module.getClass());
        if (typeId == null) {
            throw new IllegalArgumentException("Unknown module type: "
                                                + module.getClass() + " (" + module.getId() + ")");
        }
        if (StringUtils.isBlank(module.getId())) {
            throw new IllegalArgumentException("Module should has id value. " + module);
        }

        Set<ModuleRef> dependencies = eappsModuleService.getDependencies(module);

        String moduleKey = eappsModuleService.getModuleKey(module);
        String moduleId = module.getId();

        EcosModuleEntity moduleEntity = getModuleByKey(typeId, moduleKey);
        if (moduleEntity == null) {
            moduleEntity = getModule(ModuleRef.create(typeId, moduleId));
            if (moduleEntity != null) {
                moduleEntity.setKey(moduleKey);
            }
        } else {
            if (!Objects.equals(moduleEntity.getExtId(), module.getId())) {
                throw new IllegalStateException("Modules id/key collision. "
                    + "Type: " + typeId + " Key: " + moduleKey + " Id: "
                    + moduleId + " byKey: " + moduleEntity.getExtId());
            }
        }

        ModuleRef moduleRef = ModuleRef.create(typeId, moduleId);

        EcosModuleRevEntity lastModuleRev = null;

        if (moduleEntity == null) {

            log.debug("Create new module entity " + moduleRef);

            moduleEntity = new EcosModuleEntity();
            moduleEntity.setKey(moduleKey);
            moduleEntity.setExtId(moduleId);
            moduleEntity.setType(typeId);
            moduleEntity.setPublishStatus(PublishStatus.DRAFT);
            moduleEntity = moduleRepo.save(moduleEntity);

        } else {

            lastModuleRev = getLastModuleRev(typeId, moduleRef.getId());
            EcosModule currentModule = readModuleFromRev(lastModuleRev, typeId);

            if (currentModule != null) {
                module = eappsModuleService.merge(currentModule, module);
            }
        }

        byte[] data = eappsModuleService.writeAsBytes(module);
        EcosContentEntity content = contentDao.upload(data);

        if (lastModuleRev != null) {

            if (Objects.equals(lastModuleRev.getContent(), content)) {

                return new UploadStatus<>(lastModuleRev, false);

            } else if (source != null && !EcosModuleRecords.MODULES_SOURCE.equals(source)) {

                EcosModuleRevEntity lastBySource = getLastModuleRev(moduleRef, source);

                if (lastBySource != null && Objects.equals(lastBySource.getContent(), content)) {

                    return new UploadStatus<>(lastModuleRev, false);
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
        moduleEntity.setDependencies(getDependenciesModules(moduleEntity, dependencies));

        moduleRepo.save(moduleEntity);

        return new UploadStatus<>(lastModuleRev, true);
    }

    private EcosModule readModuleFromRev(EcosModuleRevEntity entity, String type) {
        if (entity == null || type == null) {
            return null;
        }
        EcosContentEntity content = entity.getContent();
        if (content == null) {
            return null;
        }
        byte[] data = content.getData();
        if (data == null) {
            return null;
        }
        try {
            return eappsModuleService.read(data, type);
        } catch (Exception e) {
            log.error("Error with entity " + entity.getId() + " " + entity.getExtId() + " " + type, e);
            return null;
        }
    }

    private Set<EcosModuleDepEntity> getDependenciesModules(EcosModuleEntity baseEntity, Set<ModuleRef> modules) {

        Set<EcosModuleDepEntity> dependencies = new HashSet<>();

        for (ModuleRef ref : modules) {

            EcosModuleEntity moduleEntity = moduleRepo.getByExtId(ref.getType(), ref.getId());
            if (moduleEntity == null) {
                moduleEntity = new EcosModuleEntity();
                moduleEntity.setExtId(ref.getId());
                moduleEntity.setType(ref.getType());
                moduleEntity = moduleRepo.save(moduleEntity);
            }

            EcosModuleDepEntity depEntity = new EcosModuleDepEntity();
            depEntity.setSource(baseEntity);
            depEntity.setTarget(moduleEntity);
            dependencies.add(depEntity);
        }

        return new HashSet<>(dependencies);
    }

    public List<EcosModuleEntity> getDependentModules(ModuleRef targetRef) {

        EcosModuleEntity moduleEntity = moduleRepo.getByExtId(targetRef.getType(), targetRef.getId());
        List<EcosModuleDepEntity> depsByTarget = moduleDepRepo.getDepsByTarget(moduleEntity.getId());

        return depsByTarget.stream()
            .map(EcosModuleDepEntity::getSource)
            .collect(Collectors.toList());
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

    public EcosModuleEntity getModuleByKey(String type, String key) {
        return moduleRepo.findByTypeAndKey(type, key);
    }

    public EcosModuleRevEntity getLastModuleRevByKey(String type, String key) {
        EcosModuleEntity entity = moduleRepo.findByTypeAndKey(type, key);
        if (entity != null) {
            return entity.getLastRev();
        }
        return null;
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
