package ru.citeck.ecos.apps.app.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.app.AppStatus;
import ru.citeck.ecos.apps.app.application.exceptions.DowngrageIsNotSupported;
import ru.citeck.ecos.apps.app.module.EcosModuleDao;
import ru.citeck.ecos.apps.app.module.ModulePublishService;
import ru.citeck.ecos.apps.domain.EcosAppEntity;
import ru.citeck.ecos.apps.domain.EcosAppRevEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;
import ru.citeck.ecos.apps.module.type.EcosModuleRev;
import ru.citeck.ecos.apps.queue.ModulePublishResultMsg;
import ru.citeck.ecos.apps.repository.EcosAppRepo;
import ru.citeck.ecos.apps.repository.EcosAppRevRepo;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class EcosAppService {

    private EcosAppRepo appRepo;
    private EcosModuleDao moduleDao;
    private EcosAppRevRepo appRevRepo;
    private ModulePublishService publishService;

    public EcosAppService(EcosAppRevRepo appRevRepo,
                          EcosAppRepo appRepo,
                          EcosModuleDao moduleDao,
                          ModulePublishService publishService) {

        this.appRepo = appRepo;
        this.appRevRepo = appRevRepo;
        this.moduleDao = moduleDao;
        this.publishService = publishService;
    }

    public void publish(EcosAppRev appRev) {

        EcosAppRevEntity appEntity = appRevRepo.getByExtId(appRev.getRevId());

        AppStatus status = appEntity.getStatus();
        if (!status.isPublishAllowed()) {
            log.warn("Publish is not allowed for status: " + status);
            return;
        }

        List<AppStatus> statuses = appRev.getModules()
            .stream()
            .map(this::publish)
            .collect(Collectors.toList());

        appEntity.setStatus(getAppStatus(statuses));
        appRevRepo.save(appEntity);
    }

    private AppStatus publish(EcosModuleRev module) {

        EcosModuleRevEntity entity = moduleDao.getModuleRev(module.getRevId());

        if (!entity.getStatus().isPublishAllowed()) {
            log.warn("Publish is not allowed for status: " + entity.getStatus());
            return entity.getStatus();
        }

        entity.setStatus(AppStatus.PUBLISHING);
        moduleDao.save(entity);
        publishService.publish(module);

        return entity.getStatus();
    }

    //todo
    /*public EcosModuleRev upload(EcosModule module) {

        EcosModuleRevEntity entity = moduleDao.uploadModule(module);
        List<EcosAppEntity> apps = appRepo.getAppsByModuleId(module.getType(), module.getId());

        for (EcosAppEntity app : apps) {
        }

        new EcosModuleDb(moduleDao.uploadModule(module));
    }*/

    public EcosAppRev upload(EcosApp app, boolean publish) {

        UploadResult uploadResult = uploadImpl(app);
        EcosAppRev appRev = new EcosAppDb(uploadResult.getAppRevEntity());

        if (publish && uploadResult.isUploaded()) {
            publish(appRev);
        } else {
            updateStatus(uploadResult.getAppRevEntity());
        }

        return appRev;
    }

    private EcosAppRevEntity updateStatus(EcosAppRevEntity entity) {

        AppStatus status = getAppStatus(
            entity.getModules()
                  .stream()
                  .map(EcosModuleRevEntity::getStatus)
                  .collect(Collectors.toList())
        );

        if (!status.equals(entity.getStatus())) {
            entity.setStatus(status);
            entity = appRevRepo.save(entity);
        }

        return entity;
    }

    private AppStatus getAppStatus(List<AppStatus> statuses) {

        AppStatus status;

        if (statuses.stream().anyMatch(AppStatus.PUBLISHING::equals)) {
            status = AppStatus.PUBLISHING;
        } else if (statuses.stream().anyMatch(AppStatus.PUBLISH_FAILED::equals)) {
            status = AppStatus.PUBLISH_FAILED;
        } else {
            status = AppStatus.PUBLISHED;
        }

        return status;
    }

    private UploadResult uploadImpl(EcosApp app) {

        EcosAppEntity appEntity = appRepo.getByExtId(app.getId());

        if (appEntity == null) {
            appEntity = new EcosAppEntity();
            appEntity.setExtId(app.getId());
            appEntity.setVersion(app.getVersion().toString());
            appEntity = appRepo.save(appEntity);
        } else {
            AppVersion currentVersion = new AppVersion(appEntity.getVersion());
            if (!app.getVersion().isAfterOrEqual(currentVersion)) {
                throw new DowngrageIsNotSupported(currentVersion, app);
            }
        }

        boolean wasUploaded = false;

        EcosAppRevEntity uploadRev = appEntity.getUploadRev();

        if (uploadRev == null
            || uploadRev.getSize() != app.getSize()
            || !app.getHash().equals(uploadRev.getHash())) {

            log.info("Start application uploading: " + app.getId() + ":" + app.getVersion());

            uploadRev = addAppRevision(appEntity, app);
            appEntity.setUploadRev(uploadRev);
            appEntity.setVersion(uploadRev.getVersion());
            appRepo.save(appEntity);

            List<EcosModuleRevEntity> modules = moduleDao.uploadModules(app.getModules());
            uploadRev.setModules(new HashSet<>(modules));
            uploadRev = appRevRepo.save(uploadRev);

            wasUploaded = true;

        } else {
            log.info("Application " + app.getId() + " already uploaded");
        }

        return new UploadResult(uploadRev, wasUploaded);
    }

    public void updatePublishStatus(ModulePublishResultMsg msg) {

        PageRequest page = PageRequest.of(0, 1000);

        List<EcosAppRevEntity> entities = appRevRepo.getAppsByModuleRev(AppStatus.PUBLISHING, msg.getRevId(), page);

        for (EcosAppRevEntity entity : entities) {

            if (!msg.isSuccess()) {
                entity.setStatus(AppStatus.PUBLISH_FAILED);
                appRevRepo.save(entity);
            } else {
                updateStatus(entity);
            }
        }
    }

    private EcosAppRevEntity addAppRevision(EcosAppEntity entity, EcosApp app) {

        EcosAppRevEntity rev = new EcosAppRevEntity();
        rev.setExtId(UUID.randomUUID().toString());
        rev.setHash(app.getHash());
        rev.setName(app.getName());
        rev.setVersion(app.getVersion().toString());
        rev.setApplication(entity);
        rev.setSize(app.getSize());
        rev.setSource(app.getSource());

        return appRevRepo.save(rev);
    }
}
