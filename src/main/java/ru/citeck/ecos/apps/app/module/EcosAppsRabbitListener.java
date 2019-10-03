package ru.citeck.ecos.apps.app.module;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.application.EcosAppService;
import ru.citeck.ecos.apps.queue.EcosAppQueues;
import ru.citeck.ecos.apps.queue.ModulePublishResultMsg;

import java.util.Map;

@Slf4j
@Component
public class EcosAppsRabbitListener {

    private EcosModuleService moduleService;
    private EcosAppService ecosAppService;

    public EcosAppsRabbitListener(EcosModuleService moduleService,
                                  EcosAppService ecosAppService) {
        this.moduleService = moduleService;
        this.ecosAppService = ecosAppService;
    }

    @RabbitListener(queues = {EcosAppQueues.PUBLISH_RESULT_ID})
    void onPublishResultReceived(ModulePublishResultMsg msg) {
        log.info("Publish status: " + msg);
        moduleService.updatePublishStatus(msg.getRevId(), msg.isSuccess(), msg.getMsg());
    }

    @RabbitListener(queues = {EcosAppQueues.ECOS_APPS_UPLOAD_ID})
    void onAppUploadReceived(Message message) {

        try {

            String source = null;
            MessageProperties props = message.getMessageProperties();
            if (props != null) {
                Map<String, Object> headers = props.getHeaders();
                if (headers != null) {
                    Object headerVal = headers.get(EcosAppQueues.SOURCE_HEADER);
                    source = headerVal != null ? String.valueOf(headerVal) : null;
                }
            }

            if (source == null) {
                source = "Queue: " + EcosAppQueues.ECOS_APPS_UPLOAD_ID;
            }

            ecosAppService.uploadApp(source, message.getBody());

        } catch (Exception e) {
            log.error("Application can't be uploaded", e);
        }
    }
}
