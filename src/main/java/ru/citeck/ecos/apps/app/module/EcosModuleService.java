package ru.citeck.ecos.apps.app.module;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.EcosAppsApiFactory;
import ru.citeck.ecos.apps.app.PublishPolicy;
import ru.citeck.ecos.apps.app.PublishStatus;
import ru.citeck.ecos.apps.app.UploadStatus;
import ru.citeck.ecos.apps.app.module.event.ModuleStatusChanged;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class EcosModuleService {

    private static final int PUBLISH_MSG_MAX = 1000;

    private final EcosModuleDao dao;
    private final ApplicationEventPublisher eventPublisher;
    private final EcosAppsApiFactory appsApi;
    private final EappsModuleService eappsModuleService;

    public EcosModuleService(ApplicationEventPublisher eventPublisher,
                             EcosAppsApiFactory appsApi,
                             EappsModuleService eappsModuleService,
                             EcosModuleDao dao) {
        this.dao = dao;
        this.appsApi = appsApi;
        this.eventPublisher = eventPublisher;
        this.eappsModuleService = eappsModuleService;
    }

    public boolean isExists(ModuleRef ref) {
        EcosModuleEntity module = dao.getModule(ref);
        return module != null;
    }

    public void delete(ModuleRef ref) {
        dao.delete(ref);
    }

    public String uploadModule(String source, EcosModule module, PublishPolicy publishPolicy) {

        if (publishPolicy == null) {
            publishPolicy = PublishPolicy.NONE;
        }

        UploadStatus<EcosModuleRevEntity> uploadStatus = dao.uploadModule(source, module);

        EcosModuleRevEntity moduleRevEntity = uploadStatus.getEntity();
        EcosModuleEntity moduleEntity = moduleRevEntity.getModule();

        Supplier<PublishStatus> statusSupplier = () -> moduleRevEntity.getModule().getPublishStatus();

        if (publishPolicy.shouldPublish(uploadStatus.isChanged(), statusSupplier)) {
            publishModule(ModuleRef.create(moduleEntity.getType(), moduleEntity.getExtId()), true);
        }

        return moduleEntity.getExtId();
    }

    public int getCount(String type) {
        return dao.getModulesCount(type);
    }

    public int getCount() {
        return dao.getModulesCount();
    }

    public List<EcosModule> getModules(String type, int skipCount, int maxItems) {

        return dao.getModulesLastRev(type, skipCount, maxItems)
            .stream()
            .map(this::toModule)
            .collect(Collectors.toList());
    }

    public List<EcosModule> getAllModules(int skipCount, int maxItems) {
        return dao.getAllLastRevisions(skipCount, maxItems)
            .stream()
            .map(this::toModule)
            .collect(Collectors.toList());
    }

    public List<EcosModule> getAllModules() {
        return getAllModules(0, 1000);
    }

    public EcosModuleRev getLastModuleRev(ModuleRef moduleRef) {
        return new EcosModuleDb(dao.getLastModuleRev(moduleRef));
    }

    public EcosModuleRev getLastModuleRev(ModuleRef moduleRef, String source) {
        return new EcosModuleDb(dao.getLastModuleRev(moduleRef, source));
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
            EcosModule moduleInstance = eappsModuleService.read(data, moduleRef.getType());

            appsApi.getModuleApi().publishModule(lastModuleRev.getExtId(), moduleInstance);

        } else {

            log.info("Module doesn't changed. Do nothing. ref: " + moduleRef);
        }
    }

    public void updatePublishStatus(String revExtId, boolean isSuccess, String message) {

        EcosModuleRevEntity entity = dao.getModuleRev(revExtId);
        if (entity == null) {
            log.warn("Module revision doesn't exists: " + revExtId + ". Publish status can't be updated");
            return;
        }

        EcosModuleEntity module = entity.getModule();
        EcosModuleRevEntity lastRev = dao.getLastModuleRev(module.getType(), module.getExtId());

        if (!Objects.equals(entity.getId(), lastRev.getId())) {
            log.info("Module revision is out of date. Current: "
                + lastRev.getId()
                + " Received: " + entity.getId()
                + ". Publish status can't be updated");
            return;
        }

        PublishStatus newStatus;
        if (isSuccess) {
            newStatus = PublishStatus.PUBLISHED;
        } else {
            newStatus = PublishStatus.PUBLISH_FAILED;
        }

        if (newStatus.equals(module.getPublishStatus())) {
            log.info("Module publish status doesn't changed. Do nothing. Message: " + message);
            return;
        }

        module.setPublishStatus(newStatus);

        if (StringUtils.isNotBlank(message)) {
            if (message.length() > PUBLISH_MSG_MAX) {
                log.warn("Publish message is too long (max " + PUBLISH_MSG_MAX + "). Message: " + message);
                message = message.substring(0, PUBLISH_MSG_MAX - 3) + "...";
            }
        }

        module.setPublishMsg(message);

        module = dao.save(module);
        dao.save(entity);

        eventPublisher.publishEvent(new ModuleStatusChanged(module));
    }

    private EcosModule toModule(EcosModuleRevEntity entity) {

        String type = entity.getModule().getType();
        byte[] content = entity.getContent().getData();

        return eappsModuleService.read(content, type);
    }
}
