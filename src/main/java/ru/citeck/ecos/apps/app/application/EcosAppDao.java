package ru.citeck.ecos.apps.app.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.AppStatus;
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

    public EcosAppRevEntity uploadApp(EcosContentEntity content) {

        EcosApp app = parser.parseData(content.getData());

        if (app.getModules().isEmpty()) {
            throw new ApplicationWithoutModules(app.getId(), app.getName());
        }

        EcosAppEntity appEntity = appRepo.getByExtId(app.getId());
        AppVersion currentVersion = new AppVersion(appEntity != null ? appEntity.getVersion() : "0");

        if (appEntity == null) {

            appEntity = new EcosAppEntity();
            appEntity.setExtId(app.getId());

        } else if (!app.getVersion().isAfterOrEqual(currentVersion)) {

            throw new DowngrageIsNotSupported(currentVersion, app);

        } else if (Objects.equals(appEntity.getUploadContent(), content)) {

            log.info("Application " + app.getId() + " (" + app.getName() + ") doesn't changed");
            return null;
        }

        appEntity.setVersion(app.getVersion().toString());
        appEntity.setUploadContent(content);
        appEntity = appRepo.save(appEntity);

        List<EcosModuleRevEntity> modulesUploadResult = uploadModules(content.getSource(), app.getModules());

        EcosAppRevEntity appRev = new EcosAppRevEntity();
        appRev.setApplication(appEntity);
        appRev.setModules(new HashSet<>(modulesUploadResult));
        appRev.setName(app.getName());
        appRev.setExtId(UUID.randomUUID().toString());
        appRev.setVersion(app.getVersion().toString());

        appRev = appRevRepo.save(appRev);

        return appRev;
    }

    public EcosAppRevEntity save(EcosAppRevEntity appRev) {
        return appRevRepo.save(appRev);
    }

    public EcosAppRevEntity getRevByExtId(String extId) {
        return appRevRepo.getByExtId(extId);
    }

    public EcosAppRevEntity getLastRevision(String appExtId) {
        List<EcosAppRevEntity> revisions = appRevRepo.getAppRevisions(appExtId, PageRequest.of(0, 1));
        return revisions.stream().findFirst().orElse(null);
    }

    public List<EcosAppRevEntity> getAppsRevByModuleRev(AppStatus status, String revId, Pageable page) {
        return appRevRepo.getAppsByModuleRev(status, revId, page);
    }

    private List<EcosModuleRevEntity> uploadModules(String source, List<EcosModule> modules) {

        List<EcosModuleRevEntity> resultModules = new ArrayList<>();

        modules.forEach(m -> {

            EcosModuleEntity module = moduleDao.getModuleByExtId(m.getType(), m.getId());
            EcosModuleRevEntity lastModuleRev = null;

            if (module == null) {

                module = new EcosModuleEntity();
                module.setExtId(m.getId());
                module.setType(m.getType());
                module = moduleDao.save(module);

            } else {

                lastModuleRev = moduleDao.getLastModuleRev(m.getType(), m.getId());
            }

            EcosContentEntity content = contentDao.upload(source, m.getData());

            if (lastModuleRev == null) {

                lastModuleRev = new EcosModuleRevEntity();
                lastModuleRev.setDataType(m.getDataType());
                lastModuleRev.setExtId(UUID.randomUUID().toString());
                lastModuleRev.setModelVersion(m.getModelVersion());
                lastModuleRev.setName(m.getName());
                lastModuleRev.setContent(content);
                lastModuleRev.setModule(module);

                lastModuleRev = moduleDao.save(lastModuleRev);

            } else if (!Objects.equals(lastModuleRev.getContent(), content)) {

                lastModuleRev.setDataType(m.getDataType());
                lastModuleRev.setModelVersion(m.getModelVersion());
                lastModuleRev.setName(m.getName());
                lastModuleRev.setContent(content);

                lastModuleRev = moduleDao.save(lastModuleRev);

            } else {
                log.info("Module " + m.getId() + " (" + m.getName() + ") doesn't changed");
            }

            resultModules.add(lastModuleRev);
        });

        return resultModules;
    }
}
