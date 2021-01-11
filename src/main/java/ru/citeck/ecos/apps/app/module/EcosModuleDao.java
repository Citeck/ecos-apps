package ru.citeck.ecos.apps.app.module;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.DeployStatus;
import ru.citeck.ecos.apps.app.UploadStatus;
import ru.citeck.ecos.apps.app.content.EcosContentDao;
import ru.citeck.ecos.apps.domain.EcosContentEntity;
import ru.citeck.ecos.apps.domain.EcosModuleDepEntity;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;
import ru.citeck.ecos.apps.module.ModuleRef;
import ru.citeck.ecos.apps.module.handler.ModuleMeta;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;
import ru.citeck.ecos.apps.module.local.LocalModulesService;
import ru.citeck.ecos.apps.module.type.ModuleTypeService;
import ru.citeck.ecos.apps.module.type.TypeContext;
import ru.citeck.ecos.apps.repository.EcosModuleDepRepo;
import ru.citeck.ecos.apps.repository.EcosModuleRevRepo;
import ru.citeck.ecos.apps.repository.EcosModuleRepo;
import ru.citeck.ecos.records2.RecordRef;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EcosModuleDao {

    private final EcosModuleRepo moduleRepo;
    private final EcosContentDao contentDao;
    private final EcosModuleRevRepo moduleRevRepo;
    private final EcosModuleDepRepo moduleDepRepo;
    private final ModuleTypeService moduleTypeService;
    private final LocalModulesService localModulesService;
    private final EcosAppsModuleTypeService ecosAppsModuleTypeService;

    public int getModulesCount() {
        return (int) moduleRepo.getCount();
    }

    public int getModulesCount(String type) {
        return (int) moduleRepo.getCount(type);
    }

    public List<EcosModuleEntity> getModulesByType(String type) {
        return moduleRepo.findAllByType(type);
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

    public void removePatchedRev(ModuleRef moduleRef) {
        EcosModuleEntity module = getModule(moduleRef);
        if (module != null && module.getPatchedRev() != null) {
            module.setDeployStatus(DeployStatus.DRAFT);
            module.setPatchedRev(null);
            save(module);
        }
    }

    public UploadStatus<EcosModuleEntity, EcosModuleRevEntity> uploadModule(String source,
                                                                            String type,
                                                                            ModuleWithMeta<Object> module,
                                                                            boolean userModule) {

        TypeContext typeCtx = moduleTypeService.getType(type);

        if (typeCtx == null) {
            throw new IllegalArgumentException("Unknown module type: " + type);
        }

        ModuleMeta meta = module.getMeta();
        List<RecordRef> dependencies = meta.getDependencies();
        if (dependencies == null) {
            dependencies = Collections.emptyList();
        }

        if (StringUtils.isBlank(meta.getId())) {
            throw new IllegalArgumentException("Module should has id value. " + meta);
        }

        EcosModuleEntity moduleEntity = getModule(ModuleRef.create(type, meta.getId()));

        ModuleRef moduleRef = ModuleRef.create(type, meta.getId());

        EcosModuleRevEntity lastModuleRev = null;
        EcosModuleRevEntity lastCreatedModuleRev = null;

        if (moduleEntity == null) {

            log.debug("Create new module entity " + moduleRef);

            moduleEntity = new EcosModuleEntity();
            moduleEntity.setExtId(meta.getId());
            moduleEntity.setType(type);
            moduleEntity.setDeployStatus(DeployStatus.DRAFT);
            moduleEntity = moduleRepo.save(moduleEntity);

        } else {
            EcosModuleRevEntity userRev = moduleEntity.getUserRev();
            EcosModuleRevEntity lastBaseRev = moduleEntity.getLastRev();
            if (userModule) {
                lastModuleRev = userRev;
            } else {
                lastModuleRev = lastBaseRev;
            }
            lastCreatedModuleRev = lastBaseRev;
            if (lastBaseRev == null ||
                    userRev != null && userRev.getCreatedDate().isAfter(lastBaseRev.getCreatedDate())) {
                lastCreatedModuleRev = userRev;
            }
        }

        byte[] data = localModulesService.writeAsBytes(module.getModule(), type);
        EcosContentEntity content = contentDao.upload(data);

        if (lastModuleRev != null && Objects.equals(lastModuleRev.getContent(), content)) {
            if (!userModule) {
                moduleEntity.setDependencies(getDependenciesModules(
                    moduleEntity,
                    new HashSet<>(dependencies)
                ));
                moduleEntity = moduleRepo.save(moduleEntity);
            }
            return new UploadStatus<>(moduleEntity, lastModuleRev, false);
        }

        log.debug("Create new module revision entity " + moduleRef);

        lastModuleRev = new EcosModuleRevEntity();
        lastModuleRev.setSource(source);
        lastModuleRev.setExtId(UUID.randomUUID().toString());
        lastModuleRev.setContent(content);
        lastModuleRev.setModule(moduleEntity);
        lastModuleRev.setIsUserRev(userModule);
        lastModuleRev.setRevType(userModule ? ModuleRevType.USER : ModuleRevType.BASE);
        lastModuleRev.setPrevRev(lastCreatedModuleRev);
        lastModuleRev = moduleRevRepo.save(lastModuleRev);

        if (userModule) {
            moduleEntity.setUserRev(lastModuleRev);
        } else {
            moduleEntity.setLastRev(lastModuleRev);
            moduleEntity.setDeployStatus(DeployStatus.DRAFT);
            moduleEntity.setDependencies(getDependenciesModules(moduleEntity, new HashSet<>(dependencies)));
        }

        moduleEntity = moduleRepo.save(moduleEntity);

        return new UploadStatus<>(moduleEntity, lastModuleRev, true);
    }

    public EcosModuleEntity uploadPatchedModule(String type, ModuleWithMeta<Object> module) {

        EcosModuleEntity entity = moduleRepo.getByExtId(type, module.getMeta().getId());

        byte[] dataBytes = localModulesService.writeAsBytes(module.getModule(), entity.getType());
        EcosContentEntity content = contentDao.upload(dataBytes);

        EcosModuleRevEntity currentRev = entity.getPatchedRev();

        if (currentRev != null && Objects.equals(currentRev.getContent(), content)) {
            return entity;
        }

        EcosModuleRevEntity lastRev = entity.getLastRev();
        if (!content.equals(lastRev.getContent())) {

            log.info("Create new patch revision for module '" + entity.getType() + "$" + entity.getExtId() + "'");

            EcosModuleRevEntity patchModuleRev = new EcosModuleRevEntity();
            patchModuleRev.setSource("patch");
            patchModuleRev.setExtId(UUID.randomUUID().toString());
            patchModuleRev.setContent(content);
            patchModuleRev.setModule(entity);
            patchModuleRev.setIsUserRev(false);
            patchModuleRev.setPrevRev(lastRev);
            patchModuleRev.setRevType(ModuleRevType.PATCHED);
            patchModuleRev = moduleRevRepo.save(patchModuleRev);

            entity.setPatchedRev(patchModuleRev);

            entity.setDeployStatus(DeployStatus.DRAFT);

            List<RecordRef> dependencies = module.getMeta().getDependencies();
            if (dependencies == null) {
                dependencies = Collections.emptyList();
            }
            entity.setDependencies(getDependenciesModules(
                entity,
                new HashSet<>(dependencies)
            ));

        } else {

            entity.setPatchedRev(null);
        }

        return moduleRepo.save(entity);
    }

    private Object readModuleFromRev(EcosModuleRevEntity entity, String type) {

        if (entity == null || StringUtils.isBlank(type)) {
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
            return localModulesService.readFromBytes(data, type);
        } catch (Exception e) {
            log.error("Error with entity " + entity.getId() + " " + entity.getExtId() + " " + type, e);
            return null;
        }
    }

    private Set<EcosModuleDepEntity> getDependenciesModules(EcosModuleEntity baseEntity, Set<RecordRef> modules) {

        Set<EcosModuleDepEntity> dependencies = new HashSet<>();

        ModuleRef baseRef = ModuleRef.create(baseEntity.getType(), baseEntity.getExtId());

        for (RecordRef recRef : modules) {

            String depType = ecosAppsModuleTypeService.getType(recRef);
            if (depType.isEmpty()) {
                continue;
            }

            ModuleRef ref = ModuleRef.create(depType, recRef.getId());

            if (baseRef.equals(ref)) {
                continue;
            }

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
        EcosModuleEntity module = getModule(moduleRef);
        if (module == null) {
            return null;
        }
        return module.getLastRev();
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

    public void delete(EcosModuleEntity module) {

        if (module != null) {
            module.setExtId(module.getExtId() + "_DELETED_" + module.getId());
            module.setDeleted(true);
            moduleRepo.save(module);
        }
    }

    public void delete(ModuleRef ref) {
        delete(getModule(ref));
    }
}
