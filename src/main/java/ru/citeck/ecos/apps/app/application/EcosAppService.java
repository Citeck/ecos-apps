package ru.citeck.ecos.apps.app.application;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.app.module.EcosModuleService;
import ru.citeck.ecos.apps.domain.EcosAppRevEntity;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Slf4j
@Service
@Transactional
public class EcosAppService {

    private EcosAppDao appDao;
    private EcosModuleService moduleService;

    public EcosAppService(EcosAppDao appDao, EcosModuleService moduleService) {
        this.appDao = appDao;
        this.moduleService = moduleService;
    }

    public void publishApp(String appId) {

        EcosAppRevEntity revision = appDao.getLastRevisionByExtId(appId);
        revision.getModules().forEach(m -> {
            EcosModuleEntity module = m.getModule();
            moduleService.publishModule(module.getType(), module.getExtId());
        });
    }

    public EcosAppRev uploadApp(String source, byte[] data) {
        return new EcosAppDb(appDao.uploadApp(source, data));
    }

    public EcosAppRev uploadApp(File file) {
        return uploadApp(null, file);
    }

    public EcosAppRev uploadApp(String source, File file) {

        if (source == null) {
            source = file.getPath();
        }

        byte[] data;
        try (FileInputStream in = new FileInputStream(file)) {
            data = IOUtils.toByteArray(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return uploadApp(source, data);
    }
}
