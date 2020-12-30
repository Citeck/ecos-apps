package ru.citeck.ecos.apps.domain.artifact.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.domain.application.service.EcosArtifactTypesService;
import ru.citeck.ecos.apps.domain.artifact.dto.DeployStatus;
import ru.citeck.ecos.apps.domain.artifact.dto.EcosArtifact;
import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactPatchDto;
import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactPublishState;
import ru.citeck.ecos.apps.domain.artifact.repo.EcosArtifactEntity;
import ru.citeck.ecos.apps.domain.artifactpatch.service.ArtifactPatchService;
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity;
import ru.citeck.ecos.apps.domain.artifact.repo.EcosModuleDepEntity;
import ru.citeck.ecos.apps.domain.artifact.repo.EcosArtifactRevEntity;
import ru.citeck.ecos.apps.module.ModuleRef;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;
import ru.citeck.ecos.apps.module.local.LocalModulesService;
import ru.citeck.ecos.apps.module.remote.RemoteModulesService;
import ru.citeck.ecos.apps.module.type.TypeContext;
import ru.citeck.ecos.commands.dto.CommandError;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.io.file.EcosFile;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.records2.predicate.model.Predicate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ArtifactsService {

    private final EcosArtifactDao dao;
    private final RemoteModulesService remoteModulesService;
    private final LocalModulesService localModulesService;
    private final EcosArtifactTypesService ecosArtifactTypesService;
    private final ArtifactPatchService artifactPatchService;

    synchronized public boolean isExists(ModuleRef ref) {
        EcosArtifactEntity module = dao.getModule(ref);
        return module != null;
    }

    synchronized public void delete(ModuleRef ref) {
        dao.delete(ref);
    }

    synchronized public void uploadUserArtifact(String source, ModuleWithMeta<Object> module, String type) {
        dao.uploadModule(source, type, module, true, null);
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

    public void updateModule(ModuleRef moduleRef) {

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

            ModuleRef moduleRef = ModuleRef.create(type, module.getMeta().getId());
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

            EcosArtifactEntity entity = dao.getModule(ModuleRef.create(type, module.getMeta().getId()));

            if (entity != null && !DeployStatus.DEPLOYED.equals(entity.getDeployStatus())) {
                tryToDeploy(entity);
            }
        }
    }

    private void tryToDeploy(EcosArtifactEntity moduleEntity) {

        ModuleRef ref = ModuleRef.create(moduleEntity.getType(), moduleEntity.getExtId());

        if (moduleEntity.getDependencies()
            .stream()
            .map(EcosModuleDepEntity::getTarget)
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

        ModuleRef moduleRef = ModuleRef.create(module.getType(), module.getExtId());
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
    public EcosArtifact getLastArtifact(ModuleRef moduleRef) {
        return toModule(dao.getLastModuleRev(moduleRef)).orElse(null);
    }

    synchronized public List<EcosArtifact> getAllArtifacts() {
        return getAllArtifacts(0, 1000);
    }

    synchronized public void setEcosAppFull(List<ModuleRef> artifacts, String ecosAppId) {
        dao.setEcosAppFull(artifacts, ecosAppId);
    }

    synchronized public void removeEcosApp(String ecosAppId) {
        dao.removeEcosApp(ecosAppId);
    }

    @Nullable
    synchronized public EcosArtifactRev getLastModuleRev(ModuleRef moduleRef) {
        EcosArtifactRevEntity lastModuleRev = dao.getLastModuleRev(moduleRef);
        if (lastModuleRev == null) {
            return null;
        }
        return new EcosArtifactDb(lastModuleRev);
    }

    synchronized public EcosArtifactRev getLastModuleRev(ModuleRef moduleRef, String source) {
        return new EcosArtifactDb(dao.getLastModuleRev(moduleRef, source));
    }

    synchronized public EcosArtifactRev getLastModuleRevByKey(String type, String key) {
        EcosArtifactRevEntity rev = dao.getLastModuleRevByKey(type, key);
        return rev != null ? new EcosArtifactDb(rev) : null;
    }

    synchronized public DeployStatus getDeployStatus(ModuleRef moduleRef) {
        EcosArtifactEntity module = dao.getModule(moduleRef);
        return module.getDeployStatus();
    }

    synchronized public ArtifactPublishState getDeployState(ModuleRef moduleRef) {
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
