package ru.citeck.ecos.apps.app.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.app.EcosApp;
import ru.citeck.ecos.apps.app.PublishPolicy;
import ru.citeck.ecos.apps.app.PublishStatus;
import ru.citeck.ecos.apps.app.UploadStatus;
import ru.citeck.ecos.apps.app.io.EcosAppIO;
import ru.citeck.ecos.apps.app.module.EcosModuleService;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.apps.domain.EcosAppEntity;
import ru.citeck.ecos.apps.domain.EcosAppRevEntity;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.function.Supplier;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EcosAppService {

    private final EcosAppDao appDao;
    private final EcosModuleService moduleService;
    private final EcosAppIO appIO;

    public PublishStatus getPublishStatus(String appId) {
        EcosAppEntity ecosApp = appDao.getEcosApp(appId);
        return ecosApp.getPublishStatus();
    }

    public void updateAppPublishStatus(String id) {
        appDao.updateAppPublishStatus(id);
    }

    public void updateAppsPublishStatus(ModuleRef moduleRef) {
        appDao.updateAppsPublishStatus(moduleRef);
    }

    public void publishApp(String appId) {

        EcosAppRevEntity revision = appDao.getLastRevisionByExtId(appId);
        revision.getModules().forEach(m -> {
            EcosModuleEntity module = m.getModule();
            moduleService.publishModule(module.getType(), module.getExtId());
        });
    }

    public EcosAppRev uploadApp(String source, EcosApp app, PublishPolicy publishPolicy) {
        return uploadApp(source, appIO.writeToBytes(app), publishPolicy);
    }

    public EcosAppRev uploadApp(String source, byte[] data, PublishPolicy publishPolicy) {

        if (publishPolicy == null) {
            publishPolicy = PublishPolicy.NONE;
        }

        UploadStatus<EcosAppRevEntity> uploadStatus = appDao.uploadApp(source, data);
        EcosAppRevEntity appRev = uploadStatus.getEntity();

        Supplier<PublishStatus> statusSupplier = () -> appRev.getApplication().getPublishStatus();

        if (publishPolicy.shouldPublish(uploadStatus.isChanged(), statusSupplier)) {

            String appId = appRev.getApplication().getExtId();
            publishApp(appId);
            updateAppPublishStatus(appId);
        }

        return new EcosAppDb(appRev);
    }

    public EcosAppRev uploadApp(File file, PublishPolicy publishPolicy) {
        return uploadApp(null, file, publishPolicy);
    }

    public EcosAppRev uploadApp(String source, File file, PublishPolicy publishPolicy) {

        if (source == null) {
            source = file.getPath();
        }

        byte[] data;
        try (FileInputStream in = new FileInputStream(file)) {
            data = IOUtils.toByteArray(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return uploadApp(source, data, publishPolicy);
    }
}
