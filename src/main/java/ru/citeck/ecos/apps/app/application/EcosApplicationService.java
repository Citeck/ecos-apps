package ru.citeck.ecos.apps.app.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.citeck.ecos.apps.app.module.EcosModule;
import ru.citeck.ecos.apps.app.module.EcosModuleDao;
import ru.citeck.ecos.apps.domain.EcosAppEntity;
import ru.citeck.ecos.apps.domain.EcosAppRevEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;
import ru.citeck.ecos.apps.repository.EcosAppRepo;
import ru.citeck.ecos.apps.repository.EcosAppRevRepo;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RestController
@RequestMapping("/test")
@Slf4j
public class EcosApplicationService {

    private EcosAppReader reader;

    private EcosAppRepo appRepo;
    private EcosAppRevRepo appRevRepo;
    private EcosModuleDao moduleDao;

    public EcosApplicationService(EcosAppReader reader,
                                  EcosAppRevRepo appRevRepo,
                                  EcosAppRepo appRepo,
                                  EcosModuleDao moduleDao) {
        this.reader = reader;
        this.appRepo = appRepo;
        this.appRevRepo = appRevRepo;
        this.moduleDao = moduleDao;
    }

    @GetMapping("t")
    public String test(String loc) {
        EcosApp app = reader.read(loc);
        try (Closeable ignored = (Closeable) app) {

            EcosAppRev appRev = upload(app);
            //log.info("Upload: " + appRev.getRevId());

        } catch (IOException e) {
            //do nothing
        }
        return "OK";
    }

    public EcosAppRev upload(EcosApp app) {
        return upload(app, false);
    }

    public EcosAppRev upload(EcosApp app, boolean deploy) {

        EcosAppEntity appEntity = appRepo.getByExtId(app.getId());

        if (appEntity == null) {
            appEntity = new EcosAppEntity();
            appEntity.setExtId(app.getId());
            appEntity = appRepo.save(appEntity);
        }

        EcosAppRevEntity uploadRev = appEntity.getUploadRev();
        if (uploadRev == null
            || uploadRev.getSize() != app.getSize()
            || !app.getHash().equals(uploadRev.getHash())) {

            log.info("Start application uploading: " + app.getId() + ":" + app.getVersion());

            uploadRev = addAppRevision(appEntity, app);
            appEntity.setUploadRev(uploadRev);
            appEntity = appRepo.save(appEntity);

            List<EcosModuleRevEntity> modules = moduleDao.uploadModules(app.getModules());
            uploadRev.setModules(new HashSet<>(modules));
            appRevRepo.save(uploadRev);

        } else {
            log.info("Application " + app.getId() + " already uploaded");
        }


        return null;
    }

    private EcosAppRevEntity addAppRevision(EcosAppEntity entity, EcosApp app) {

        EcosAppRevEntity rev = new EcosAppRevEntity();
        rev.setExtId(UUID.randomUUID().toString());
        rev.setHash(app.getHash());
        rev.setName(app.getName());
        rev.setVersion(app.getVersion().toString());
        rev.setApplication(entity);
        rev.setSize(app.getSize());

        return appRevRepo.save(rev);
    }

    public EcosAppRev upload(Resource resource, boolean deploy) {
       // reader.load(resource)
        return null;
    }
}
