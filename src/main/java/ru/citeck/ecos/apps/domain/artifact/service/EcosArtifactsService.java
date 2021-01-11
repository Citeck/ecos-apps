package ru.citeck.ecos.apps.domain.artifact.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.app.domain.ecostype.service.EcosTypeArtifactsService;
import ru.citeck.ecos.apps.artifact.ArtifactMeta;
import ru.citeck.ecos.apps.artifact.ArtifactRef;
import ru.citeck.ecos.apps.artifact.ArtifactsService;
import ru.citeck.ecos.apps.artifact.type.TypeContext;
import ru.citeck.ecos.apps.domain.application.service.EcosArtifactTypesService;
import ru.citeck.ecos.apps.domain.artifact.dto.*;
import ru.citeck.ecos.apps.domain.artifact.repo.*;
import ru.citeck.ecos.apps.domain.artifact.service.upload.ArtifactUploadDto;
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity;
import ru.citeck.ecos.apps.domain.content.service.EcosContentDao;
import ru.citeck.ecos.commands.dto.CommandError;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.io.file.EcosFile;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.model.Predicate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EcosArtifactsService {

    private static final String ECOS_APP_SOURCE_TYPE = "ecos-app";

    private final ArtifactsService artifactsService;

    private final EcosContentDao contentDao;
    private final EcosArtifactsRepo artifactsRepo;
    private final EcosArtifactsDepRepo artifactsDepRepo;
    private final EcosArtifactsRevRepo artifactsRevRepo;
    private final EcosArtifactTypesService ecosArtifactTypesService;

    synchronized public boolean isExists(ArtifactRef ref) {
        EcosArtifactEntity module = dao.getModule(ref);
        return module != null;
    }

    synchronized public void delete(ArtifactRef ref) {

        dao.delete(ref);
    }

    synchronized public void uploadUserArtifact(String type, Object artifact) {

        dao.uploadModule(USER_SOURCE, type, artifact, true, null);
    }

    synchronized public void uploadEcosAppArtifacts(String ecosAppId, EcosFile artifactsDir) {

        uploadEcosAppArtifacts(ecosAppId, artifactsDir, ecosArtifactTypesService.getAllTypesCtx());
    }

    synchronized public void uploadEcosAppArtifacts(String ecosAppId, EcosFile artifactsDir, List<TypeContext> types) {

        for (TypeContext typeCtx : types) {

            List<Object> artifacts = localModulesService.readModulesForType(artifactsDir, typeCtx);
            uploadEcosAppArtifacts("ecos-app-" + ecosAppId, artifacts, typeCtx.getId(), ecosAppId);
        }
    }

    synchronized public boolean uploadUserArtifact(String type, Object artifact) {

    }

    synchronized public boolean uploadPatchedArtifact(String type, Object artifact) {

    }

    synchronized public boolean uploadArtifact(ArtifactUploadDto uploadDto) {

        String typeId = uploadDto.getType();
        Object artifact = uploadDto.getArtifact();

        TypeContext typeContext = artifactsService.getType(uploadDto.getType());

        if (typeContext == null) {
            log.error("Type '" + uploadDto.getType() + "' is not found. " +
                      "Artifact will be skipped: " + uploadDto.getArtifact());
            return false;
        }

        ArtifactMeta meta = artifactsService.getArtifactMeta(typeContext, uploadDto.getArtifact());
        if (meta == null) {
            log.error("Artifact meta can't be evaluated. Type: '" + typeId + "' Artifact: " + artifact);
            return false;
        }

        if (StringUtils.isBlank(meta.getId())) {
            log.error("Artifact id is empty. Type: '" + typeId + "' Artifact: " + artifact);
            return false;
        }

        boolean isUserRev = uploadDto.getSourceType().equals(ArtifactSourceType.USER);

        EcosArtifactRevEntity lastModuleRev = null;

        EcosArtifactEntity artifactEntity = artifactsRepo.getByExtId(typeId, meta.getId());
        if (artifactEntity == null) {

            artifactEntity = new EcosArtifactEntity();
            artifactEntity.setExtId(meta.getId());
            artifactEntity.setType(typeId);
            artifactEntity.setDeployStatus(DeployStatus.DRAFT);
            artifactEntity = artifactsRepo.save(artifactEntity);

        } else {

            EcosArtifactRevEntity userRev = artifactEntity.getUserRev();
            EcosArtifactRevEntity lastBaseRev = artifactEntity.getLastRev();

            if (isUserRev) {

                lastModuleRev = userRev;

            } else {

                lastModuleRev = lastBaseRev;

                String artifactCurrentEcosApp = artifactEntity.getEcosApp();
                String newArtifactSourceId = uploadDto.getSourceId();
                ArtifactSourceType sourceType = uploadDto.getSourceType();

                if (StringUtils.isNotBlank(artifactCurrentEcosApp)
                    && (!sourceType.equals(ArtifactSourceType.ECOS_APP)
                        || !newArtifactSourceId.equals(artifactCurrentEcosApp))) {

                    log.info("Artifact owned by " + artifactCurrentEcosApp
                        + " app and can't be updated by " + sourceType + " -> " + newArtifactSourceId);

                    return false;
                }
            }
        }

        byte[] data = artifactsService.writeArtifactAsBytes(typeContext, artifact);
        EcosContentEntity content = contentDao.upload(data);

        if (!isUserRev && lastModuleRev != null) {
            artifactEntity.setDependencies(getDependenciesModules(artifactEntity, meta.getDependencies()));
        }
        
        EcosArtifactRevEntity lastCreatedModuleRev = null;
        lastModuleRev.getApplications()


        MLText lastRevName = toNotNullMLText(meta.getName());
        MLText newName = toNotNullMLText(module.getMeta().getName());

        if (lastModuleRev != null
            && Objects.equals(lastModuleRev.getContent(), content)
            && lastRevName.equals(newName)) {

            if (!isUserRev) {
                moduleEntity.setDependencies(getDependenciesModules(
                    moduleEntity,
                    toNotNullSet(meta.getDependencies())
                ));
                moduleEntity = artifactsRepo.save(moduleEntity);
            }
            return new UploadStatus<>(moduleEntity, lastModuleRev, false);
        }

        if (!lastRevName.equals(newName)) {
            moduleEntity.setName(Json.getMapper().toString(newName));
            moduleEntity.setTags(Json.getMapper().toString(module.getMeta().getTags()));
            moduleEntity = artifactsRepo.save(moduleEntity);
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
        lastModuleRev = artifactsRevRepo.save(lastModuleRev);

        if (userModule) {
            moduleEntity.setUserRev(lastModuleRev);
        } else {
            moduleEntity.setLastRev(lastModuleRev);
            moduleEntity.setDeployStatus(DeployStatus.DRAFT);
            moduleEntity.setDependencies(getDependenciesModules(moduleEntity, toNotNullSet(meta.getDependencies())));
        }

        moduleEntity = artifactsRepo.save(moduleEntity);

        return new UploadStatus<>(moduleEntity, lastModuleRev, true);
    }

    private Set<EcosArtifactDepEntity> getDependenciesModules(EcosArtifactEntity baseEntity, Collection<RecordRef> modules) {

        Set<EcosArtifactDepEntity> dependencies = new HashSet<>();

        ArtifactRef baseRef = ArtifactRef.create(baseEntity.getType(), baseEntity.getExtId());

        Set<RecordRef> modulesSet = new HashSet<>(modules);

        for (RecordRef recRef : modulesSet) {

            String depType = ecosArtifactTypesService.getType(recRef);
            if (depType.isEmpty()) {
                continue;
            }

            ArtifactRef ref = ArtifactRef.create(depType, recRef.getId());

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

            EcosArtifactDepEntity depEntity = new EcosArtifactDepEntity();
            depEntity.setSource(baseEntity);
            depEntity.setTarget(moduleEntity);
            dependencies.add(depEntity);
        }

        return new HashSet<>(dependencies);
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

    public void updateModule(ArtifactRef moduleRef) {

        EcosArtifactEntity module = dao.getModule(moduleRef);
        if (module != null) {

            try {
                EcosArtifactRevEntity lastRev = module.getLastRev();
                byte[] lastRevData = lastRev.getContent().getData();
                Object moduleObj = localModulesService.readFromBytes(lastRevData, moduleRef.getType());

                uploadEcosAppArtifacts(
                    "update-modules",
                    Collections.singletonList(moduleObj),
                    moduleRef.getType(),
                    null
                );

            } catch (Exception e) {
                log.error("Module can't be updated. Module: '" + moduleRef + "'", e);
            }
        }
    }

    synchronized public void uploadEcosAppArtifacts(String source,
                                                    List<Object> modules,
                                                    String type,
                                                    @Nullable String ecosApp) {

        if (log.isDebugEnabled()) {
            log.debug("Modules to upload: " + modules.size() + " with type '" + type + "' and source '" + source + "'");
        }

        if (modules.isEmpty()) {
            return;
        }

        String app = ecosArtifactTypesService.getAppByModuleType(type);
        if (app.isEmpty()) {
            log.info("Application is not defined for type " + type + ". Modules can't be uploaded");
            return;
        }

        List<ModuleWithMeta<Object>> modulesMeta = remoteModulesService.prepareToDeploy(app, type, modules);
        if (modules.size() != modulesMeta.size()) {
            log.info("Modules count was changed by target app. Before: "
                + modules.size() + " After: " + modulesMeta.size());
        }

        List<Object> patchedModules = new ArrayList<>();

        for (ModuleWithMeta<Object> module : modulesMeta) {

            if (log.isDebugEnabled()) {
                log.debug("Upload module " + module.getMeta());
            }

            dao.uploadModule(source, type, module, false, ecosApp);

            ArtifactRef moduleRef = ArtifactRef.create(type, module.getMeta().getId());
            List<ArtifactPatchDto> patches = artifactPatchService.getPatches(moduleRef);
            boolean wasPatched = false;
            if (!patches.isEmpty()) {
                Object patchedData = artifactPatchService.applyPatches(module.getModule(), moduleRef, patches);
                if (!Objects.equals(patchedData, module.getModule())) {
                    patchedModules.add(patchedData);
                    wasPatched = true;
                }
            }
            if (!wasPatched) {
                dao.removePatchedRev(moduleRef);
            }
        }

        if (!patchedModules.isEmpty()) {

            List<ModuleWithMeta<Object>> patchedMeta = remoteModulesService.prepareToDeploy(app, type, patchedModules);
            if (patchedModules.size() != patchedMeta.size()) {
                log.info("Patched modules count was changed by target app. Before: "
                    + patchedModules.size() + " After: " + patchedMeta.size());
            }

            for (ModuleWithMeta<Object> module : patchedMeta) {
                dao.uploadPatchedModule(type, module);
            }
        }

        for (ModuleWithMeta<Object> module : modulesMeta) {

            EcosArtifactEntity entity = dao.getModule(ArtifactRef.create(type, module.getMeta().getId()));

            if (entity != null && !DeployStatus.DEPLOYED.equals(entity.getDeployStatus())) {
                tryToDeploy(entity);
            }
        }
    }

    private void tryToDeploy(EcosArtifactEntity moduleEntity) {

        ArtifactRef ref = ArtifactRef.create(moduleEntity.getType(), moduleEntity.getExtId());

        if (moduleEntity.getDependencies()
            .stream()
            .map(EcosArtifactDepEntity::getTarget)
            .anyMatch(d -> !DeployStatus.DEPLOYED.equals(d.getDeployStatus()))) {

            moduleEntity.setDeployStatus(DeployStatus.DEPS_WAITING);
            log.info("Module " + ref + " can't be deployed yet. Dependencies waiting");

            dao.save(moduleEntity);

        } else {

            String type = moduleEntity.getType();
            String appName = ecosArtifactTypesService.getAppByModuleType(type);

            EcosArtifactRevEntity revToDeploy = moduleEntity.getPatchedRev();
            if (revToDeploy == null) {
                revToDeploy = moduleEntity.getLastRev();
            }
            if (revToDeploy != null) {

                EcosContentEntity content = revToDeploy.getContent();

                if (content != null) {

                    Object module = localModulesService.readFromBytes(content.getData(), type);
                    List<CommandError> errors = remoteModulesService.deployModule(appName, type, module);

                    if (errors.isEmpty()) {

                        moduleEntity.setDeployStatus(DeployStatus.DEPLOYED);
                        moduleEntity.setDeployMsg("");

                        dao.save(moduleEntity);

                        tryToDeployDependentModules(moduleEntity);

                    } else {

                        String msg = errors.stream()
                            .map(CommandError::getMessage)
                            .collect(Collectors.joining("|"));

                        log.info("Module " + ref + " deploy failed. Msg: " + msg);

                        moduleEntity.setDeployStatus(DeployStatus.DEPLOY_FAILED);
                        moduleEntity.setDeployMsg(msg);

                        dao.save(moduleEntity);
                    }
                } else {

                    log.info("Module " + ref + " can't be deployed. Content is missing");
                }
            } else {

                log.info("Module " + ref + " can't be deployed. Last revision is missing");
            }
        }
    }

    private void tryToDeployDependentModules(EcosArtifactEntity module) {

        ArtifactRef moduleRef = ArtifactRef.create(module.getType(), module.getExtId());
        List<EcosArtifactEntity> modules = dao.getDependentModules(moduleRef);

        for (EcosArtifactEntity moduleFromDep : modules) {

            DeployStatus depStatus = moduleFromDep.getDeployStatus();

            if (DeployStatus.DEPS_WAITING.equals(depStatus)
                || DeployStatus.DEPLOY_FAILED.equals(depStatus)) {

                tryToDeploy(moduleFromDep);
            }
        }
    }

    synchronized public List<EcosArtifact> getModules(String type, int skipCount, int maxItems) {

        return dao.getModulesLastRev(type, skipCount, maxItems)
            .stream()
            .map(this::toModule)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    synchronized public List<EcosArtifact> getAllArtifacts(int skipCount, int maxItems) {
        return dao.getAllLastRevisions(skipCount, maxItems)
            .stream()
            .map(this::toModule)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    synchronized public long getAllArtifactsCount(Predicate predicate) {
        return dao.getCount(predicate);
    }

    synchronized public List<EcosArtifact> getAllArtifacts(Predicate predicate, int skipCount, int maxItems) {
        return dao.getAllLastRevisions(predicate, skipCount, maxItems)
            .stream()
            .map(this::toModule)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    @Nullable
    public EcosArtifact getLastArtifact(ArtifactRef moduleRef) {
        return toModule(dao.getLastModuleRev(moduleRef)).orElse(null);
    }

    synchronized public List<EcosArtifact> getAllArtifacts() {
        return getAllArtifacts(0, 1000);
    }

    synchronized public void setEcosAppFull(List<ArtifactRef> artifacts, String ecosAppId) {
        dao.setEcosAppFull(artifacts, ecosAppId);
    }

    synchronized public void removeEcosApp(String ecosAppId) {
        dao.removeEcosApp(ecosAppId);
    }

    @Nullable
    synchronized public EcosArtifactRev getLastModuleRev(ArtifactRef moduleRef) {
        EcosArtifactRevEntity lastModuleRev = dao.getLastModuleRev(moduleRef);
        if (lastModuleRev == null) {
            return null;
        }
        return new EcosArtifactDb(lastModuleRev);
    }

    synchronized public EcosArtifactRev getLastModuleRev(ArtifactRef moduleRef, String source) {
        return new EcosArtifactDb(dao.getLastModuleRev(moduleRef, source));
    }

    synchronized public EcosArtifactRev getLastModuleRevByKey(String type, String key) {
        EcosArtifactRevEntity rev = dao.getLastModuleRevByKey(type, key);
        return rev != null ? new EcosArtifactDb(rev) : null;
    }

    synchronized public DeployStatus getDeployStatus(ArtifactRef moduleRef) {
        EcosArtifactEntity module = dao.getModule(moduleRef);
        return module.getDeployStatus();
    }

    synchronized public ArtifactPublishState getDeployState(ArtifactRef moduleRef) {
        EcosArtifactEntity module = dao.getModule(moduleRef);
        return new ArtifactPublishState(module.getDeployStatus(), module.getDeployMsg());
    }

    synchronized public EcosArtifactRev getModuleRevision(String id) {
        return new EcosArtifactDb(dao.getModuleRev(id));
    }

    private Optional<EcosArtifact> toModule(EcosArtifactRevEntity entity) {

        if (entity == null) {
            return Optional.empty();
        }

        String type = entity.getModule().getType();
        byte[] content = entity.getContent().getData();

        if (ecosArtifactTypesService.isTypeRegistered(type)) {
            return Optional.of(new EcosArtifact(
                entity.getModule().getExtId(),
                localModulesService.readFromBytes(content, type),
                type,
                Json.getMapper().read(entity.getModule().getName(), MLText.class),
                DataValue.create(entity.getModule().getTags()).asStrList()
            ));
        }
        return Optional.empty();
    }
}
