package ru.citeck.ecos.apps.app.module;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.application.EcosAppService;
import ru.citeck.ecos.apps.app.content.EcosContentDao;
import ru.citeck.ecos.apps.domain.EcosContentEntity;
import ru.citeck.ecos.apps.queue.EcosAppQueues;
import ru.citeck.ecos.apps.queue.ModulePublishResultMsg;

@Slf4j
@Component
public class EcosAppsRabbitListener {

    private EcosModuleService moduleService;
    private EcosAppService ecosAppService;
    private EcosContentDao contentDao;

    public EcosAppsRabbitListener(EcosModuleService moduleService,
                                  EcosAppService ecosAppService,
                                  EcosContentDao contentDao) {
        this.moduleService = moduleService;
        this.ecosAppService = ecosAppService;
        this.contentDao = contentDao;
    }

    @RabbitListener(queues = {EcosAppQueues.PUBLISH_RESULT_ID})
    void onPublishResultReceived(ModulePublishResultMsg msg) {
        log.info("Publish status: " + msg);
        moduleService.updatePublishStatus(msg);
        ecosAppService.updatePublishStatus(msg);
    }

    @RabbitListener(queues = {EcosAppQueues.ECOS_APPS_UPLOAD_ID})
    void onAppUploadReceived(Message message) {

        try {

            String source = null;
            try {
                source = (String) message.getMessageProperties().getHeaders().get("ecos-app-source");
            } catch (Exception e) {
                //todo refactor
                log.warn("Source can't be detected", e);
            }
            if (source == null) {
                source = "Upload Queue";
            }

            EcosContentEntity entity = contentDao.upload(source, message.getBody());
            ecosAppService.uploadApp(entity, true);

        } catch (Exception e) {
            log.error("Application can't be uploaded", e);
        }
    }
}
