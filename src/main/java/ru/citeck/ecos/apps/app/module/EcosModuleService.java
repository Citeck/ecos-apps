package ru.citeck.ecos.apps.app.module;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.EcosAppsApiFactory;
import ru.citeck.ecos.apps.app.PublishStatus;
import ru.citeck.ecos.apps.app.module.event.ModuleStatusChanged;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;

import java.util.List;
import java.util.Objects;
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

    public List<EcosModule> getAllModules() {
        return dao.getAllLastRevisions().stream().map(rev -> {

            String type = rev.getModule().getType();
            byte[] content = rev.getContent().getData();

            return (EcosModule) eappsModuleService.read(content, type);

        }).collect(Collectors.toList());
    }

    public EcosModuleRev getLastModuleRev(String type, String id) {
        return new EcosModuleDb(dao.getLastModuleRev(type, id));
    }

    public EcosModuleRev getModuleRevision(String id) {
        return new EcosModuleDb(dao.getModuleRev(id));
    }

    public void publishModule(String type, String id) {

        EcosModuleRevEntity lastModuleRev = dao.getLastModuleRev(type, id);
        EcosModuleEntity module = lastModuleRev.getModule();

        module.setPublishStatus(PublishStatus.PUBLISHING);
        dao.save(module);

        EcosModule moduleInstance = eappsModuleService.read(lastModuleRev.getContent().getData(), type);
        appsApi.getModuleApi().publishModule(lastModuleRev.getExtId(), moduleInstance);

        eventPublisher.publishEvent(new ModuleStatusChanged(module));
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
        dao.save(entity);
        eventPublisher.publishEvent(new ModuleStatusChanged(module));
    }
}
