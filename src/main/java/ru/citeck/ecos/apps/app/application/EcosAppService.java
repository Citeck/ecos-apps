package ru.citeck.ecos.apps.app.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.app.AppStatus;
import ru.citeck.ecos.apps.app.application.exceptions.DowngrageIsNotSupported;
import ru.citeck.ecos.apps.app.module.EcosModuleDao;
import ru.citeck.ecos.apps.app.module.EcosModuleService;
import ru.citeck.ecos.apps.domain.EcosAppEntity;
import ru.citeck.ecos.apps.domain.EcosAppRevEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;
import ru.citeck.ecos.apps.queue.ModulePublishResultMsg;
import ru.citeck.ecos.apps.repository.EcosAppRepo;
import ru.citeck.ecos.apps.repository.EcosAppRevRepo;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class EcosAppService {

    private EcosAppRepo appRepo;
    private EcosAppRevRepo appRevRepo;
    private EcosModuleDao moduleDao;
    private EcosModuleService moduleService;

    public EcosAppService(EcosAppRevRepo appRevRepo,
                          EcosAppRepo appRepo,
                          EcosModuleDao moduleDao,
                          EcosModuleService moduleService) {

        this.appRepo = appRepo;
        this.appRevRepo = appRevRepo;
        this.moduleDao = moduleDao;
        this.moduleService = moduleService;
    }

    public void publishApp(EcosAppRev appRev) {

        EcosAppRevEntity appEntity = appRevRepo.getByExtId(appRev.getRevId());

        AppStatus status = appEntity.getStatus();
        if (!status.isPublishAllowed()) {
            log.warn("Publish is not allowed for status: " + status);
            return;
        }

        appEntity.setStatus(AppStatus.PUBLISHING);
        appRevRepo.save(appEntity);

        appRev.getModules().forEach(m -> moduleService.publish(m));
    }

    public EcosAppRev upload(EcosApp app, boolean publish) {

        EcosAppRev appRev = upload(app);
        if (publish) {
            publishApp(appRev);
        }

        return appRev;
    }

    public EcosAppRev upload(EcosApp app) {

        EcosAppEntity appEntity = appRepo.getByExtId(app.getId());

        if (appEntity == null) {
            appEntity = new EcosAppEntity();
            appEntity.setExtId(app.getId());
            appEntity = appRepo.save(appEntity);
        }

        EcosAppRevEntity lastRevision = getLastRevision(appEntity.getExtId());
        if (lastRevision != null) {
            AppVersion currentVersion = new AppVersion(lastRevision.getVersion());
            if (!app.getVersion().isAfterOrEqual(currentVersion)) {
                throw new DowngrageIsNotSupported(currentVersion, app);
            }
        }

        EcosAppRevEntity uploadRev = appEntity.getUploadRev();
        if (uploadRev == null
            || uploadRev.getSize() != app.getSize()
            || !app.getHash().equals(uploadRev.getHash())) {

            log.info("Start application uploading: " + app.getId() + ":" + app.getVersion());

            uploadRev = addAppRevision(appEntity, app);
            appEntity.setUploadRev(uploadRev);
            appRepo.save(appEntity);

            List<EcosModuleRevEntity> modules = moduleDao.uploadModules(app.getModules());
            uploadRev.setModules(new HashSet<>(modules));
            uploadRev = appRevRepo.save(uploadRev);

        } else {
            log.info("Application " + app.getId() + " already uploaded");
        }

        return new EcosAppDb(uploadRev);
    }

    public void updatePublishStatus(ModulePublishResultMsg msg) {

        PageRequest page = PageRequest.of(0, 1000);

        List<EcosAppRevEntity> entities = appRevRepo.getAppsByModuleRev(AppStatus.PUBLISHING, msg.getRevId(), page);

        for (EcosAppRevEntity entity : entities) {

            if (!msg.isSuccess()) {
                entity.setStatus(AppStatus.PUBLISH_FAILED);
                appRevRepo.save(entity);
            } else {
                boolean isAllPublished = entity.getModules()
                    .stream()
                    .allMatch(m -> AppStatus.PUBLISHED.equals(m.getStatus()));

                if (isAllPublished) {
                    entity.setStatus(AppStatus.PUBLISHED);
                    appRevRepo.save(entity);
                }
            }
        }
    }

    private EcosAppRevEntity getLastRevision(String extAppId) {
        Pageable page = PageRequest.of(0, 1);
        List<EcosAppRevEntity> lastRevList = appRevRepo.getAppRevisions(extAppId, page);
        return lastRevList.stream().findFirst().orElse(null);
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
