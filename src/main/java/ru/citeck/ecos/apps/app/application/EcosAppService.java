package ru.citeck.ecos.apps.app.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.app.module.EcosModuleDao;
import ru.citeck.ecos.apps.app.module.EcosModuleService;
import ru.citeck.ecos.apps.repository.EcosAppRevDepRepo;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EcosAppService {

    private final EcosAppDao appDao;
    private final EcosModuleDao moduleDao;
    private final EcosModuleService moduleService;
    private final EcosAppRevDepRepo appDepsRepo;

    public void publishApp(String appId) {

        //EcosAppRevEntity revision = appDao.getLastRevisionByExtId(appId);
        /*revision.getModules().forEach(m -> {
            EcosModuleEntity module = m.getModule();
            moduleService.publishModule(ModuleRef.create(module.getType(), module.getExtId()), false);
        });*/
    }

/*
    public EcosAppRev uploadApp(String source, EcosApp app, PublishPolicy publishPolicy) {
        return uploadApp(source, appIO.writeToBytes(app), publishPolicy);
    }
*/

  /*  public EcosAppRev uploadApp(String source, byte[] data, PublishPolicy publishPolicy) {

        if (publishPolicy == null) {
            publishPolicy = PublishPolicy.NONE;
        }

        UploadStatus<EcosAppRevEntity> uploadStatus = appDao.uploadApp(source, data);
        EcosAppRevEntity appRev = uploadStatus.getEntity();

        Supplier<PublishStatus> statusSupplier = () -> appRev.getApplication().getDeployStatus();

        if (publishPolicy.shouldPublish(uploadStatus.isChanged(), statusSupplier)) {
            tryToPublish(appRev.getApplication());
        }

        return new EcosAppDb(appRev);
    }

    private void tryToPublish(EcosAppEntity application) {

        EcosAppRevEntity lastRevision = appDao.getLastRevisionByAppId(application.getId());

        Set<PublishStatus> depsStatuses = lastRevision.getDependencies()
            .stream()
            .map(EcosAppRevDepEntity::getTarget)
            .map(EcosAppEntity::getDeployStatus)
            .collect(Collectors.toSet());

        if (depsStatuses.stream().allMatch(PublishStatus.DEPLOYED::equals)) {
            String appId = application.getExtId();
            publishApp(appId);
            updateAppPublishStatus(appId);
        } else {
            PublishStatus publishStatus = application.getDeployStatus();
            if (!PublishStatus.DEPS_WAITING.equals(publishStatus)) {
                application.setPublishStatus(PublishStatus.DEPS_WAITING);
                appDao.save(application);
            }
        }
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

    public PublishStatus getDeployStatus(String appId) {
        EcosAppEntity ecosApp = appDao.getEcosApp(appId);
        return ecosApp.getDeployStatus();
    }

    public void updateAppsPublishStatus(ModuleRef moduleRef) {

        EcosModuleRevEntity lastModuleRev = moduleDao.getLastModuleRev(moduleRef);

        if (lastModuleRev == null) {
            log.warn("Module doesn't exists: " + moduleRef);
            return;
        }

        lastModuleRev.getApplications()
            .stream()
            .map(EcosAppRevEntity::getApplication)
            .forEach(this::updateAppPublishStatus);
    }

    public void updateAppPublishStatus(String id) {
        EcosAppEntity entity = appDao.getEcosApp(id);
        updateAppPublishStatus(entity);
    }

    private void updateAppPublishStatus(EcosAppEntity entity) {

        EcosAppRevEntity lastRevision = appDao.getLastRevisionByAppId(entity.getId());

        PublishStatus newStatus = getAppStatus(
            lastRevision.getModules()
                .stream()
                .map(me -> me.getModule().getDeployStatus())
                .collect(Collectors.toList())
        );

        PublishStatus statusBefore = entity.getDeployStatus();

        if (!newStatus.equals(statusBefore)) {

            entity.setPublishStatus(newStatus);
            appDao.save(entity);

            if (newStatus.equals(PublishStatus.DEPLOYED) && statusBefore.equals(PublishStatus.PUBLISHING)) {

                List<EcosAppRevDepEntity> deps = appDepsRepo.getDepsByTarget(entity.getId());
                deps.stream()
                    .map(EcosAppRevDepEntity::getSource)
                    .map(EcosAppRevEntity::getApplication)
                    .filter(a -> PublishStatus.DEPS_WAITING.equals(a.getDeployStatus()))
                    .forEach(this::tryToPublish);
            }
        }
    }

    private PublishStatus getAppStatus(List<PublishStatus> statuses) {

        PublishStatus status;

        if (statuses.stream().anyMatch(PublishStatus.PUBLISHING::equals)) {
            status = PublishStatus.PUBLISHING;
        } else if (statuses.stream().anyMatch(PublishStatus.PUBLISH_FAILED::equals)) {
            status = PublishStatus.PUBLISH_FAILED;
        } else {
            status = PublishStatus.DEPLOYED;
        }

        return status;
    }*/
}
