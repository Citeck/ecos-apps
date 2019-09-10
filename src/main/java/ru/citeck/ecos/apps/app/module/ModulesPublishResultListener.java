package ru.citeck.ecos.apps.app.module;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.application.EcosAppService;
import ru.citeck.ecos.apps.queue.EcosAppQueues;
import ru.citeck.ecos.apps.queue.ModulePublishResultMsg;

@Slf4j
@Component
public class ModulesPublishResultListener {

    private EcosModuleService moduleService;
    private EcosAppService ecosAppService;

    public ModulesPublishResultListener(EcosModuleService moduleService,
                                        EcosAppService ecosAppService) {
        this.moduleService = moduleService;
        this.ecosAppService = ecosAppService;
    }

    @RabbitListener(queues = {EcosAppQueues.PUBLISH_RESULT_ID})
    void onPublishResultReceived(ModulePublishResultMsg msg) {
        log.info("Publish status: " + msg);
        moduleService.updatePublishStatus(msg);
        ecosAppService.updatePublishStatus(msg);
    }
}
