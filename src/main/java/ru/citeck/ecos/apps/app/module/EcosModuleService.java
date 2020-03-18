package ru.citeck.ecos.apps.app.module;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.app.DeployStatus;
import ru.citeck.ecos.apps.app.UploadStatus;
import ru.citeck.ecos.apps.domain.EcosContentEntity;
import ru.citeck.ecos.apps.domain.EcosModuleDepEntity;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;
import ru.citeck.ecos.apps.module.ModuleRef;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;
import ru.citeck.ecos.apps.module.local.LocalModulesService;
import ru.citeck.ecos.apps.module.remote.RemoteModulesService;
import ru.citeck.ecos.commands.dto.CommandError;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EcosModuleService {

    private final EcosModuleDao dao;
    private final RemoteModulesService remoteModulesService;
    private final LocalModulesService localModulesService;
    private final EcosAppsModuleTypeService ecosAppsModuleTypeService;

    synchronized public boolean isExists(ModuleRef ref) {
        EcosModuleEntity module = dao.getModule(ref);
        return module != null;
    }

    synchronized public void delete(ModuleRef ref) {
        dao.delete(ref);
    }

    synchronized public void uploadUserModule(String source, ModuleWithMeta<Object> module, String type) {
        dao.uploadModule(source, type, module, true);
    }

    synchronized public void uploadModules(String source, List<Object> modules, String type) {

        if (modules.isEmpty()) {
            return;
        }

        String app = ecosAppsModuleTypeService.getAppByModuleType(type);
        if (app.isEmpty()) {
            log.info("Application is not defined for type " + type + ". Modules can't be uploaded");
        }

        List<ModuleWithMeta<Object>> modulesMeta = remoteModulesService.prepareToDeploy(app, type, modules);
        if (modules.size() != modulesMeta.size()) {
            log.info("Modules count was changed by target app. Before: "
                + modules.size() + " After: " + modulesMeta.size());
        }

        for (ModuleWithMeta<Object> module : modulesMeta) {

            UploadStatus<EcosModuleEntity, EcosModuleRevEntity> uploadStatus =
                dao.uploadModule(source, type, module, false);

            if (!DeployStatus.DEPLOYED.equals(uploadStatus.getEntity().getDeployStatus())) {
                tryToDeploy(uploadStatus.getEntity());
            }
        }
    }

    private void tryToDeploy(EcosModuleEntity moduleEntity) {

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
            String appName = ecosAppsModuleTypeService.getAppByModuleType(type);

            EcosModuleRevEntity lastRev = moduleEntity.getLastRev();
            if (lastRev != null) {

                EcosContentEntity content = lastRev.getContent();

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

    private void tryToDeployDependentModules(EcosModuleEntity module) {

        ModuleRef moduleRef = ModuleRef.create(module.getType(), module.getExtId());
        List<EcosModuleEntity> modules = dao.getDependentModules(moduleRef);

        for (EcosModuleEntity moduleFromDep : modules) {

            DeployStatus depStatus = moduleFromDep.getDeployStatus();

            if (DeployStatus.DEPS_WAITING.equals(depStatus)
                || DeployStatus.DEPLOY_FAILED.equals(depStatus)) {

                tryToDeploy(moduleFromDep);
            }
        }
    }

    synchronized public List<EcosModule> getModules(String type, int skipCount, int maxItems) {

        return dao.getModulesLastRev(type, skipCount, maxItems)
            .stream()
            .map(this::toModule)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    synchronized public List<EcosModule> getAllModules(int skipCount, int maxItems) {
        return dao.getAllLastRevisions(skipCount, maxItems)
            .stream()
            .map(this::toModule)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    synchronized public List<EcosModule> getAllModules() {
        return getAllModules(0, 1000);
    }

    synchronized public EcosModuleRev getLastModuleRev(ModuleRef moduleRef) {
        EcosModuleRevEntity lastModuleRev = dao.getLastModuleRev(moduleRef);
        if (lastModuleRev == null) {
            return null;
        }
        return new EcosModuleDb(lastModuleRev);
    }

    synchronized public EcosModuleRev getLastModuleRev(ModuleRef moduleRef, String source) {
        return new EcosModuleDb(dao.getLastModuleRev(moduleRef, source));
    }

    synchronized public EcosModuleRev getLastModuleRevByKey(String type, String key) {
        EcosModuleRevEntity rev = dao.getLastModuleRevByKey(type, key);
        return rev != null ? new EcosModuleDb(rev) : null;
    }

    synchronized public DeployStatus getDeployStatus(ModuleRef moduleRef) {
        EcosModuleEntity module = dao.getModule(moduleRef);
        return module.getDeployStatus();
    }

    synchronized public ModulePublishState getDeployState(ModuleRef moduleRef) {
        EcosModuleEntity module = dao.getModule(moduleRef);
        return new ModulePublishState(module.getDeployStatus(), module.getDeployMsg());
    }

    synchronized public EcosModuleRev getModuleRevision(String id) {
        return new EcosModuleDb(dao.getModuleRev(id));
    }

    private Optional<EcosModule> toModule(EcosModuleRevEntity entity) {

        String type = entity.getModule().getType();
        byte[] content = entity.getContent().getData();

        if (ecosAppsModuleTypeService.isTypeRegistered(type)) {
            return Optional.of(new EcosModule(localModulesService.readFromBytes(content, type), type));
        }
        return Optional.empty();
    }
}
