package ru.citeck.ecos.apps.app.module;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.app.PublishPolicy;
import ru.citeck.ecos.apps.app.PublishStatus;
import ru.citeck.ecos.apps.app.UploadStatus;
import ru.citeck.ecos.apps.domain.EcosModuleDepEntity;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;
import ru.citeck.ecos.apps.module.ModuleRef;
import ru.citeck.ecos.apps.module.local.LocalModulesService;
import ru.citeck.ecos.apps.module.remote.RemoteModulesService;
import ru.citeck.ecos.commands.dto.CommandError;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
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

    public boolean isExists(ModuleRef ref) {
        EcosModuleEntity module = dao.getModule(ref);
        return module != null;
    }

    public void delete(ModuleRef ref) {
        dao.delete(ref);
    }

    public String uploadModule(String source, String type, Object module) {
        return uploadModule(source, type, module, PublishPolicy.PUBLISH_IF_NOT_PUBLISHED);
    }

    public String uploadModule(String source, String type, Object module, PublishPolicy publishPolicy) {

        if (publishPolicy == null) {
            publishPolicy = PublishPolicy.NONE;
        }

        UploadStatus<EcosModuleRevEntity> uploadStatus = dao.uploadModule(source, type, module);

        EcosModuleRevEntity moduleRevEntity = uploadStatus.getEntity();
        EcosModuleEntity moduleEntity = moduleRevEntity.getModule();

        Supplier<PublishStatus> statusSupplier = () -> moduleRevEntity.getModule().getPublishStatus();

        if (publishPolicy.shouldPublish(uploadStatus.isChanged(), statusSupplier)) {
            tryToPublish(moduleEntity);
        }

        return moduleEntity.getExtId();
    }

    private void tryToPublish(EcosModuleEntity moduleEntity) {

        if (moduleEntity.getDependencies()
                        .stream()
                        .map(EcosModuleDepEntity::getTarget)
                        .anyMatch(d -> !PublishStatus.PUBLISHED.equals(d.getPublishStatus()))) {

            moduleEntity.setPublishStatus(PublishStatus.DEPS_WAITING);
            dao.save(moduleEntity);
        } else {
            publishModule(ModuleRef.create(moduleEntity.getType(), moduleEntity.getExtId()), true);
        }
    }

    public int getCount(String type) {
        return dao.getModulesCount(type);
    }

    public int getCount() {
        return dao.getModulesCount();
    }

    public List<Object> getModules(String type, int skipCount, int maxItems) {

        return dao.getModulesLastRev(type, skipCount, maxItems)
            .stream()
            .map(this::toModule)
            .collect(Collectors.toList());
    }

    public List<Object> getAllModules(int skipCount, int maxItems) {
        return dao.getAllLastRevisions(skipCount, maxItems)
            .stream()
            .map(this::toModule)
            .collect(Collectors.toList());
    }

    public List<Object> getAllModules() {
        return getAllModules(0, 1000);
    }

    public EcosModuleRev getLastModuleRev(ModuleRef moduleRef) {
        EcosModuleRevEntity lastModuleRev = dao.getLastModuleRev(moduleRef);
        if (lastModuleRev == null) {
            return null;
        }
        return new EcosModuleDb(lastModuleRev);
    }

    public EcosModuleRev getLastModuleRev(ModuleRef moduleRef, String source) {
        return new EcosModuleDb(dao.getLastModuleRev(moduleRef, source));
    }

    public EcosModuleRev getLastModuleRevByKey(String type, String key) {
        EcosModuleRevEntity rev = dao.getLastModuleRevByKey(type, key);
        return rev != null ? new EcosModuleDb(rev) : null;
    }

    public PublishStatus getPublishStatus(ModuleRef moduleRef) {
        EcosModuleEntity module = dao.getModule(moduleRef);
        return module.getPublishStatus();
    }

    public ModulePublishState getPublishState(ModuleRef moduleRef) {
        EcosModuleEntity module = dao.getModule(moduleRef);
        return new ModulePublishState(module.getPublishStatus(), module.getPublishMsg());
    }

    public EcosModuleRev getModuleRevision(String id) {
        return new EcosModuleDb(dao.getModuleRev(id));
    }

    public void publishModule(ModuleRef moduleRef, boolean force) {

        log.info("Start module publishing: " + moduleRef);

        EcosModuleRevEntity lastModuleRev = dao.getLastModuleRev(moduleRef);
        EcosModuleEntity module = lastModuleRev.getModule();

        PublishStatus currentStatus = module.getPublishStatus();

        if (force || !PublishStatus.PUBLISHED.equals(currentStatus)) {

            log.info("Send module to publish API. ref: " + moduleRef);

            module.setPublishStatus(PublishStatus.PUBLISHING);
            dao.save(module);

            byte[] data = lastModuleRev.getContent().getData();
            Object moduleInstance = localModulesService.readFromBytes(data, moduleRef.getType());
            String appName = ecosAppsModuleTypeService.getAppByModuleType(moduleRef.getType());

            List<CommandError> errors = remoteModulesService.publishModule(appName,
                moduleRef.getType(),
                moduleInstance,
                Instant.now().toEpochMilli());

            if (errors.isEmpty()) {

                module.setPublishStatus(PublishStatus.PUBLISHED);
                module.setPublishMsg("");

            } else {

                errors.forEach(err -> {
                    log.error("Publish error '" + err.getType() + "': " + err.getMessage());
                    List<String> stackTrace = err.getStackTrace();
                    for (String traceLine : stackTrace) {
                        log.error(traceLine);
                    }
                });
                String msg = '"' + errors.stream().map(CommandError::getMessage)
                    .collect(Collectors.joining("\",\"")) + '"';

                module.setPublishStatus(PublishStatus.PUBLISH_FAILED);
                module.setPublishMsg(msg);
            }

            dao.save(module);

        } else {

            log.info("Module already published. Do nothing. ref: " + moduleRef);
        }
    }

    private Object toModule(EcosModuleRevEntity entity) {

        String type = entity.getModule().getType();
        byte[] content = entity.getContent().getData();

        return localModulesService.readFromBytes(content, type);
    }
}
