package ru.citeck.ecos.apps.app.application;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.PublishStatus;
import ru.citeck.ecos.apps.app.AppVersion;
import ru.citeck.ecos.apps.app.application.exceptions.ApplicationWithoutModules;
import ru.citeck.ecos.apps.app.application.exceptions.DowngrageIsNotSupported;
import ru.citeck.ecos.apps.app.content.EcosContentDao;
import ru.citeck.ecos.apps.app.module.EcosModuleDao;
import ru.citeck.ecos.apps.domain.*;
import ru.citeck.ecos.apps.module.type.EcosModule;
import ru.citeck.ecos.apps.repository.EcosAppRepo;
import ru.citeck.ecos.apps.repository.EcosAppRevRepo;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EcosAppDao {

    private EcosAppParser parser;

    private EcosAppRepo appRepo;
    private EcosAppRevRepo appRevRepo;
    private EcosContentDao contentDao;
    private EcosModuleDao moduleDao;

    public EcosAppDao(EcosAppParser parser,
                      EcosAppRepo appRepo,
                      EcosAppRevRepo appRevRepo,
                      EcosContentDao contentDao,
                      EcosModuleDao moduleDao) {

        this.parser = parser;
        this.appRepo = appRepo;
        this.appRevRepo = appRevRepo;
        this.contentDao = contentDao;
        this.moduleDao = moduleDao;
    }

    public EcosAppRevEntity uploadApp(String source, byte[] data) {

        EcosApp app = parser.parseData(data);

        log.info("Start application uploading: " + app.getName() + " (" + app.getId() + "). Source: " + source);

        EcosAppEntity appEntity = appRepo.getByExtId(app.getId());
        EcosAppRevEntity appLastRev = null;

        if (appEntity == null) {

            appEntity = new EcosAppEntity();
            appEntity.setExtId(app.getId());

        } else {

            appLastRev = getLastRevisionByAppId(appEntity.getId());
        }

        Set<EcosModuleRevEntity> uploadedModules = app.getModules()
            .stream()
            .map(m -> moduleDao.uploadModule(source, m))
            .collect(Collectors.toSet());

        if (appLastRev == null || !appLastRev.getModules().equals(uploadedModules)) {

            String currVersionStr = appEntity.getVersion();
            AppVersion currentVersion = new AppVersion(StringUtils.isNotBlank(currVersionStr) ? currVersionStr : "0");

            appEntity.setVersion(app.getVersion().toString());
            appRepo.save(appEntity);

            appLastRev = new EcosAppRevEntity();
            appLastRev.setApplication(appEntity);
            appLastRev.setModules(new HashSet<>(uploadedModules));
            appLastRev.setName(app.getName());
            appLastRev.setExtId(UUID.randomUUID().toString());
            appLastRev.setVersion(app.getVersion().toString());

            appLastRev = appRevRepo.save(appLastRev);

        } else {

            log.info("Application doesn't changed: " + app.getName() + " (" + app.getId() + ")");
        }

        return appLastRev;
    }

    public EcosAppEntity save(EcosAppEntity appEntity) {
        return appRepo.save(appEntity);
    }

    public EcosAppRevEntity getRevByExtId(String extId) {
        return appRevRepo.getByExtId(extId);
    }

    public EcosAppRevEntity getLastRevisionByExtId(String appExtId) {
        List<EcosAppRevEntity> revisions = appRevRepo.getAppRevisions(appExtId, PageRequest.of(0, 1));
        return revisions.stream().findFirst().orElse(null);
    }

    public EcosAppRevEntity getLastRevisionByAppId(long appId) {
        List<EcosAppRevEntity> revisions = appRevRepo.getAppRevisions(appId, PageRequest.of(0, 1));
        return revisions.stream().findFirst().orElse(null);
    }

    public List<EcosAppRevEntity> getAppsRevByModuleRev(PublishStatus status, String revId, Pageable page) {
        return appRevRepo.getAppsByModuleRev(status, revId, page);
    }

    public void updatePublishStatus(EcosModuleEntity entity) {

        EcosModuleRevEntity lastModuleRev = moduleDao.getLastModuleRev(entity.getType(), entity.getExtId());

        lastModuleRev.getApplications()
            .stream()
            .map(EcosAppRevEntity::getApplication)
            .forEach(this::updatePublishStatus);
    }

    public EcosAppEntity updatePublishStatus(EcosAppEntity entity) {

        EcosAppRevEntity lastRevision = getLastRevisionByAppId(entity.getId());

        PublishStatus status = getAppStatus(
            lastRevision.getModules()
                .stream()
                .map(me -> me.getModule().getPublishStatus())
                .collect(Collectors.toList())
        );

        if (!status.equals(entity.getPublishStatus())) {
            entity.setPublishStatus(status);
            entity = appRepo.save(entity);
        }

        return entity;
    }

    private PublishStatus getAppStatus(List<PublishStatus> statuses) {

        PublishStatus status;

        if (statuses.stream().anyMatch(PublishStatus.PUBLISHING::equals)) {
            status = PublishStatus.PUBLISHING;
        } else if (statuses.stream().anyMatch(PublishStatus.PUBLISH_FAILED::equals)) {
            status = PublishStatus.PUBLISH_FAILED;
        } else {
            status = PublishStatus.PUBLISHED;
        }

        return status;
    }
}
