package ru.citeck.ecos.apps.domain.artifact.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.domain.application.service.EcosArtifactTypesService;
import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactRevType;
import ru.citeck.ecos.apps.domain.artifact.dto.DeployStatus;
import ru.citeck.ecos.apps.domain.artifact.dto.UploadStatus;
import ru.citeck.ecos.apps.domain.artifact.repo.*;
import ru.citeck.ecos.apps.domain.content.service.EcosContentDao;
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity;
import ru.citeck.ecos.apps.domain.artifact.repo.EcosArtifactEntity;
import ru.citeck.ecos.apps.module.ModuleRef;
import ru.citeck.ecos.apps.module.handler.ModuleMeta;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;
import ru.citeck.ecos.apps.module.local.LocalModulesService;
import ru.citeck.ecos.apps.module.type.ModuleTypeService;
import ru.citeck.ecos.apps.module.type.TypeContext;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.PredicateUtils;
import ru.citeck.ecos.records2.predicate.model.Predicate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EcosArtifactDao {

    private final EcosModuleRepo moduleRepo;
    private final EcosContentDao contentDao;
    private final EcosArtifactRevRepo moduleRevRepo;
    private final EcosModuleDepRepo moduleDepRepo;
    private final ModuleTypeService moduleTypeService;
    private final LocalModulesService localModulesService;
    private final EcosArtifactTypesService ecosArtifactTypesService;

    public int getModulesCount() {
        return (int) moduleRepo.getCount();
    }

    public int getModulesCount(String type) {
        return (int) moduleRepo.getCount(type);
    }

    public List<EcosArtifactEntity> getModulesByType(String type) {
        return moduleRepo.findAllByType(type);
    }

    public List<EcosArtifactEntity> getAllModules() {
        return moduleRepo.findAll();
    }

    public List<EcosArtifactRevEntity> getModulesLastRev(String type, int skipCount, int maxItems) {
        int page = skipCount / maxItems;
        return moduleRepo.getModulesLastRev(type, PageRequest.of(page, maxItems));
    }

    public List<EcosArtifactRevEntity> getAllLastRevisions(int skipCount, int maxItems) {

        int page = skipCount / maxItems;
        return moduleRepo.findAll(PageRequest.of(page, maxItems))
            .stream()
            .map(EcosArtifactEntity::getLastRev)
            .collect(Collectors.toList());
    }

    public List<EcosArtifactRevEntity> getAllLastRevisions(Predicate predicate, int skipCount, int maxItems) {

        Specification<EcosArtifactEntity> spec = toSpec(predicate);

        int page = skipCount / maxItems;
        return moduleRepo.findAll(spec, PageRequest.of(page, maxItems))
            .stream()
            .map(EcosArtifactEntity::getLastRev)
            .collect(Collectors.toList());
    }

    public long getCount(Predicate predicate) {
        Specification<EcosArtifactEntity> spec = toSpec(predicate);
        return moduleRepo.count(spec);
    }

    public void removePatchedRev(ModuleRef moduleRef) {
        EcosArtifactEntity module = getModule(moduleRef);
        if (module != null && module.getPatchedRev() != null) {
            module.setDeployStatus(DeployStatus.DRAFT);
            module.setPatchedRev(null);
            save(module);
        }
    }

    public UploadStatus<EcosArtifactEntity, EcosArtifactRevEntity> uploadModule(String source,
                                                                                String type,
                                                                                ModuleWithMeta<Object> module,
                                                                                boolean userModule,
                                                                                String ecosApp) {

        TypeContext typeCtx = moduleTypeService.getType(type);

        if (typeCtx == null) {
            throw new IllegalArgumentException("Unknown module type: " + type);
        }

        ModuleMeta meta = module.getMeta();

        if (StringUtils.isBlank(meta.getId())) {
            throw new IllegalArgumentException("Module should has id value. " + meta);
        }

        EcosArtifactEntity moduleEntity = getModule(ModuleRef.create(type, meta.getId()));

        ModuleRef moduleRef = ModuleRef.create(type, meta.getId());

        EcosArtifactRevEntity lastModuleRev = null;
        EcosArtifactRevEntity lastCreatedModuleRev = null;

        if (moduleEntity == null) {

            log.debug("Create new module entity " + moduleRef);

            moduleEntity = new EcosArtifactEntity();
            moduleEntity.setExtId(meta.getId());
            moduleEntity.setType(type);
            moduleEntity.setDeployStatus(DeployStatus.DRAFT);
            moduleEntity = moduleRepo.save(moduleEntity);

        } else {

            EcosArtifactRevEntity userRev = moduleEntity.getUserRev();
            EcosArtifactRevEntity lastBaseRev = moduleEntity.getLastRev();

            if (userModule) {

                lastModuleRev = userRev;

            } else {

                lastModuleRev = lastBaseRev;

                if (StringUtils.isNotBlank(moduleEntity.getEcosApp())
                        && !Objects.equals(moduleEntity.getEcosApp(), ecosApp)) {

                    // module owned by other ECOS application
                    return new UploadStatus<>(moduleEntity, lastModuleRev, false);
                }
            }
            lastCreatedModuleRev = lastBaseRev;
            if (lastBaseRev == null ||
                    userRev != null && userRev.getCreatedDate().isAfter(lastBaseRev.getCreatedDate())) {
                lastCreatedModuleRev = userRev;
            }
        }

        byte[] data = localModulesService.writeAsBytes(module.getModule(), type);
        EcosContentEntity content = contentDao.upload(data);

        MLText lastRevName = toNotNullMLText(moduleEntity.getName());
        MLText newName = toNotNullMLText(module.getMeta().getName());

        if (lastModuleRev != null
                && Objects.equals(lastModuleRev.getContent(), content)
                && lastRevName.equals(newName)) {

            if (!userModule) {
                moduleEntity.setDependencies(getDependenciesModules(
                    moduleEntity,
                    toNotNullSet(meta.getDependencies())
                ));
                moduleEntity = moduleRepo.save(moduleEntity);
            }
            return new UploadStatus<>(moduleEntity, lastModuleRev, false);
        }

        if (!lastRevName.equals(newName)) {
            moduleEntity.setName(Json.getMapper().toString(newName));
            moduleEntity.setTags(Json.getMapper().toString(module.getMeta().getTags()));
            moduleEntity = moduleRepo.save(moduleEntity);
        }

        log.debug("Create new module revision entity " + moduleRef);

        lastModuleRev = new EcosArtifactRevEntity();
        lastModuleRev.setSource(source);
        lastModuleRev.setExtId(UUID.randomUUID().toString());
        lastModuleRev.setContent(content);
        lastModuleRev.setModule(moduleEntity);
        lastModuleRev.setIsUserRev(userModule);
        lastModuleRev.setRevType(userModule ? ArtifactRevType.USER : ArtifactRevType.BASE);
        lastModuleRev.setPrevRev(lastCreatedModuleRev);
        lastModuleRev = moduleRevRepo.save(lastModuleRev);

        if (userModule) {
            moduleEntity.setUserRev(lastModuleRev);
        } else {
            moduleEntity.setLastRev(lastModuleRev);
            moduleEntity.setDeployStatus(DeployStatus.DRAFT);
            moduleEntity.setDependencies(getDependenciesModules(moduleEntity, toNotNullSet(meta.getDependencies())));
        }

        moduleEntity = moduleRepo.save(moduleEntity);

        return new UploadStatus<>(moduleEntity, lastModuleRev, true);
    }

    private MLText toNotNullMLText(String value) {
        if (StringUtils.isBlank(value)) {
            return new MLText();
        }
        return Json.getMapper().read(value, MLText.class);
    }

    private MLText toNotNullMLText(MLText text) {
        return text != null ? text : new MLText();
    }

    @Nullable
    private String toStringOrNull(Object value) {
        if (value == null
                || (value instanceof Collection && ((Collection<?>) value).isEmpty())
                || (value instanceof Map && ((Map<?, ?>) value).isEmpty())) {
            return null;
        }
        return Json.getMapper().toString(value);
    }

    public EcosArtifactEntity uploadPatchedModule(String type, ModuleWithMeta<Object> module) {

        EcosArtifactEntity entity = moduleRepo.getByExtId(type, module.getMeta().getId());

        byte[] dataBytes = localModulesService.writeAsBytes(module.getModule(), entity.getType());
        EcosContentEntity content = contentDao.upload(dataBytes);

        EcosArtifactRevEntity currentRev = entity.getPatchedRev();

        if (currentRev != null && Objects.equals(currentRev.getContent(), content)) {
            return entity;
        }

        EcosArtifactRevEntity lastRev = entity.getLastRev();
        if (!content.equals(lastRev.getContent())) {

            log.info("Create new patch revision for module '" + entity.getType() + "$" + entity.getExtId() + "'");

            EcosArtifactRevEntity patchModuleRev = new EcosArtifactRevEntity();
            patchModuleRev.setSource("patch");
            patchModuleRev.setExtId(UUID.randomUUID().toString());
            patchModuleRev.setContent(content);
            patchModuleRev.setModule(entity);
            patchModuleRev.setIsUserRev(false);
            patchModuleRev.setPrevRev(lastRev);
            patchModuleRev.setRevType(ArtifactRevType.PATCHED);
            patchModuleRev = moduleRevRepo.save(patchModuleRev);

            entity.setPatchedRev(patchModuleRev);

            entity.setDeployStatus(DeployStatus.DRAFT);
            entity.setDependencies(getDependenciesModules(
                entity,
                toNotNullSet(module.getMeta().getDependencies())
            ));

        } else {

            entity.setPatchedRev(null);
        }

        return moduleRepo.save(entity);
    }

    @NotNull
    private <T> Set<T> toNotNullSet(@Nullable List<T> list) {
        return list != null ? new HashSet<>(list) : Collections.emptySet();
    }

    private Object readModuleFromRev(EcosArtifactRevEntity entity, String type) {

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

    private Set<EcosModuleDepEntity> getDependenciesModules(EcosArtifactEntity baseEntity, Set<RecordRef> modules) {

        Set<EcosModuleDepEntity> dependencies = new HashSet<>();

        ModuleRef baseRef = ModuleRef.create(baseEntity.getType(), baseEntity.getExtId());

        for (RecordRef recRef : modules) {

            String depType = ecosArtifactTypesService.getType(recRef);
            if (depType.isEmpty()) {
                continue;
            }

            ModuleRef ref = ModuleRef.create(depType, recRef.getId());

            if (baseRef.equals(ref)) {
                continue;
            }

            EcosArtifactEntity moduleEntity = moduleRepo.getByExtId(ref.getType(), ref.getId());
            if (moduleEntity == null) {
                moduleEntity = new EcosArtifactEntity();
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

    synchronized public void setEcosAppFull(List<ModuleRef> artifacts, String ecosAppId) {

        List<EcosArtifactEntity> currentArtifacts = moduleRepo.findAllByEcosApp(ecosAppId);
        for (EcosArtifactEntity artifact : currentArtifacts) {
            ModuleRef currentModuleRef = ModuleRef.create(artifact.getType(), artifact.getExtId());
            if (!artifacts.contains(currentModuleRef)) {
                artifact.setEcosApp(null);
                moduleRepo.save(artifact);
            }
        }

        for (ModuleRef artifactRef : artifacts) {

            EcosArtifactEntity moduleEntity = moduleRepo.getByExtId(artifactRef.getType(), artifactRef.getId());

            if (moduleEntity != null && StringUtils.isNotBlank(moduleEntity.getEcosApp())) {

                if (!moduleEntity.getEcosApp().equals(ecosAppId)) {
                    throw new IllegalArgumentException("Artifact " + artifactRef
                        + " already included in " + moduleEntity.getEcosApp() + " application");
                }
            }

            if (moduleEntity == null) {
                moduleEntity = new EcosArtifactEntity();
                moduleEntity.setExtId(artifactRef.getId());
                moduleEntity.setType(artifactRef.getType());
                moduleEntity = moduleRepo.save(moduleEntity);
            }

            moduleEntity.setEcosApp(ecosAppId);
            moduleRepo.save(moduleEntity);
        }
    }

    synchronized public void removeEcosApp(String ecosAppId) {

        List<EcosArtifactEntity> currentArtifacts = moduleRepo.findAllByEcosApp(ecosAppId);
        for (EcosArtifactEntity artifact : currentArtifacts) {
            artifact.setEcosApp(null);
            moduleRepo.save(artifact);
        }
    }

    public List<EcosArtifactEntity> getDependentModules(ModuleRef targetRef) {

        EcosArtifactEntity moduleEntity = moduleRepo.getByExtId(targetRef.getType(), targetRef.getId());
        List<EcosModuleDepEntity> depsByTarget = moduleDepRepo.getDepsByTarget(moduleEntity.getId());

        return depsByTarget.stream()
            .map(EcosModuleDepEntity::getSource)
            .collect(Collectors.toList());
    }

    public EcosArtifactRevEntity getLastModuleRev(String type, String id) {
        return getLastModuleRev(ModuleRef.create(type, id));
    }

    public EcosArtifactRevEntity getLastModuleRev(ModuleRef moduleRef, String source) {

        Pageable page = PageRequest.of(0, 1);

        List<EcosArtifactRevEntity> rev = moduleRevRepo.getModuleRevisions(moduleRef.getType(),
                                                                         moduleRef.getId(),
                                                                         source, page);

        return rev.stream().findFirst().orElse(null);
    }

    public EcosArtifactRevEntity getLastModuleRev(ModuleRef moduleRef) {
        EcosArtifactEntity module = getModule(moduleRef);
        if (module == null) {
            return null;
        }
        return module.getLastRev();
    }

    public EcosArtifactEntity getModule(ModuleRef ref) {
        return moduleRepo.getByExtId(ref.getType(), ref.getId());
    }

    public EcosArtifactEntity getModuleByKey(String type, String key) {
        return moduleRepo.findByTypeAndKey(type, key);
    }

    public EcosArtifactRevEntity getLastModuleRevByKey(String type, String key) {
        EcosArtifactEntity entity = moduleRepo.findByTypeAndKey(type, key);
        if (entity != null) {
            return entity.getLastRev();
        }
        return null;
    }

    public EcosArtifactRevEntity getModuleRev(String revId) {
        return moduleRevRepo.getRevByExtId(revId);
    }

    public EcosArtifactEntity save(EcosArtifactEntity entity) {
        return moduleRepo.save(entity);
    }

    public EcosArtifactRevEntity save(EcosArtifactRevEntity entity) {
        return moduleRevRepo.save(entity);
    }

    public void delete(EcosArtifactEntity module) {

        if (module != null) {
            module.setExtId(module.getExtId() + "_DELETED_" + module.getId());
            module.setDeleted(true);
            moduleRepo.save(module);
        }
    }

    public void delete(ModuleRef ref) {
        delete(getModule(ref));
    }

    private Specification<EcosArtifactEntity> toSpec(Predicate predicate) {

        PredicateDto predicateDto = PredicateUtils.convertToDto(predicate, PredicateDto.class);
        Specification<EcosArtifactEntity> spec = null;

        if (StringUtils.isNotBlank(predicateDto.name)) {
            spec = (root, query, builder) ->
                builder.like(builder.lower(root.get("name")), "%" + predicateDto.name.toLowerCase() + "%");
        }
        if (StringUtils.isNotBlank(predicateDto.moduleId)) {
            Specification<EcosArtifactEntity> idSpec = (root, query, builder) ->
                builder.like(builder.lower(root.get("extId")), "%" + predicateDto.moduleId.toLowerCase() + "%");
            spec = spec != null ? spec.or(idSpec) : idSpec;
        }
        if (StringUtils.isNotBlank(predicateDto.type)) {
            Specification<EcosArtifactEntity> typeSpec = (root, query, builder) ->
                builder.like(builder.lower(root.get("type")), "%" + predicateDto.type.toLowerCase() + "%");
            spec = spec != null ? spec.or(typeSpec) : typeSpec;
        }
        String tags = predicateDto.tags;
        if (StringUtils.isBlank(tags)) {
            tags = predicateDto.tagsStr;
        }
        String finalTags = tags;
        if (StringUtils.isNotBlank(finalTags)) {
            Specification<EcosArtifactEntity> tagsSpec = (root, query, builder) ->
                builder.like(
                    builder.lower(root.get("tags")), "%" + finalTags.toLowerCase() + "%"
                );
            spec = spec != null ? spec.or(tagsSpec) : tagsSpec;
        }

        Specification<EcosArtifactEntity> sourceIdFilter = (root, query, builder) ->
            root.get("type").in(ecosArtifactTypesService.getNonInternalTypes());

        if (spec != null) {
            spec = spec.and(sourceIdFilter);
        } else {
            spec = sourceIdFilter;
        }

        return spec;
    }

    @Data
    public static class PredicateDto {
        private String name;
        private String type;
        private String tags;
        private String tagsStr;
        private String moduleId;
    }
}
