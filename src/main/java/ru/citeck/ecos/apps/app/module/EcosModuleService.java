package ru.citeck.ecos.apps.app.module;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.app.AppStatus;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;
import ru.citeck.ecos.apps.module.type.EcosModuleRev;
import ru.citeck.ecos.apps.queue.ModulePublishResultMsg;

@Slf4j
@Service
@Transactional
public class EcosModuleService {

    private static final int PUBLISH_MSG_MAX = 1000;

    private EcosModuleDao dao;
    private ModulePublishService publishService;

    public EcosModuleService(EcosModuleDao dao, ModulePublishService publishService) {
        this.dao = dao;
        this.publishService = publishService;
    }

    public EcosModuleRev getLastModuleRev(String type, String id) {
        return new EcosModuleDb(dao.getLastModuleRev(type, id));
    }

    public EcosModuleRev getModuleRevision(String id) {
        return new EcosModuleDb(dao.getModuleRev(id));
    }

    public void publish(EcosModuleRev module) {

        EcosModuleRevEntity entity = dao.getModuleRev(module.getRevId());
        if (!entity.getStatus().isPublishAllowed()) {
            log.warn("Publish is not allowed for status: " + entity.getStatus());
            return;
        }
        entity.setStatus(AppStatus.PUBLISHING);
        dao.save(entity);

        publishService.publish(module);
    }

    public void updatePublishStatus(ModulePublishResultMsg msg) {

        EcosModuleRevEntity entity = dao.getModuleRev(msg.getRevId());

        String publishMsg = msg.getMsg();
        if (StringUtils.isNotBlank(publishMsg)) {
            if (publishMsg.length() > PUBLISH_MSG_MAX) {
                log.warn("Publish message is too long (max " + PUBLISH_MSG_MAX + "). Message: " + publishMsg);
                publishMsg = publishMsg.substring(0, PUBLISH_MSG_MAX - 3) + "...";
            }
        }

        entity.setPublishMsg(publishMsg);

        if (msg.isSuccess()) {
            entity.setStatus(AppStatus.PUBLISHED);
        } else {
            entity.setStatus(AppStatus.PUBLISH_FAILED);
        }

        dao.save(entity);
    }
}
