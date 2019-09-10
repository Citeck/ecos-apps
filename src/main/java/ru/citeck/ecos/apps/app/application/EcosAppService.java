package ru.citeck.ecos.apps.app.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.app.AppStatus;
import ru.citeck.ecos.apps.app.module.EcosModuleDao;
import ru.citeck.ecos.apps.app.module.ModulePublishService;
import ru.citeck.ecos.apps.domain.EcosAppRevEntity;
import ru.citeck.ecos.apps.domain.EcosContentEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;
import ru.citeck.ecos.apps.module.type.EcosModuleRev;
import ru.citeck.ecos.apps.queue.ModulePublishResultMsg;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class EcosAppService {

    private EcosAppDao appDao;
    private EcosModuleDao moduleDao;
    private ModulePublishService publishService;

    public EcosAppService(EcosAppDao appDao,
                          EcosModuleDao moduleDao,
                          ModulePublishService publishService) {

        this.appDao = appDao;
        this.moduleDao = moduleDao;
        this.publishService = publishService;
    }

    private void publish(EcosAppRev appRev) {

        EcosAppRevEntity appEntity = appDao.getRevByExtId(appRev.getRevId());

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
        appDao.save(appEntity);
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

    public EcosAppRev uploadApp(EcosContentEntity content, boolean publish) {

        EcosAppRevEntity entity = appDao.uploadApp(content);

        EcosAppRev rev = null;
        if (entity != null && publish) {
            rev = new EcosAppDb(entity);
            publish(rev);
        }

        return rev;
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
            entity = appDao.save(entity);
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

    public void updatePublishStatus(ModulePublishResultMsg msg) {

        PageRequest page = PageRequest.of(0, 1000);

        List<EcosAppRevEntity> entities = appDao.getAppsRevByModuleRev(AppStatus.PUBLISHING, msg.getRevId(), page);

        for (EcosAppRevEntity entity : entities) {

            if (!msg.isSuccess()) {
                entity.setStatus(AppStatus.PUBLISH_FAILED);
                appDao.save(entity);
            } else {
                updateStatus(entity);
            }
        }
    }
}
