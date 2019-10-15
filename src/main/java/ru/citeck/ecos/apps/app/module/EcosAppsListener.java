package ru.citeck.ecos.apps.app.module;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.EcosAppsApiFactory;
import ru.citeck.ecos.apps.app.api.EcosAppDeployMsg;
import ru.citeck.ecos.apps.app.application.EcosAppService;
import ru.citeck.ecos.apps.app.module.api.ModulePublishResultMsg;

@Slf4j
@Component
public class EcosAppsListener {

    private EcosModuleService moduleService;
    private EcosAppService ecosAppService;

    private EcosAppsApiFactory apiFactory;

    private boolean initialized = false;

    public EcosAppsListener(EcosModuleService moduleService,
                            EcosAppService ecosAppService,
                            EcosAppsApiFactory apiFactory) {
        this.moduleService = moduleService;
        this.ecosAppService = ecosAppService;
        this.apiFactory = apiFactory;
    }

    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {

        if (!initialized) {

            log.info("Init MQ listeners");

            apiFactory.getModuleApi().onModulePublishResult(this::onPublishResultReceived);
            apiFactory.getAppApi().onAppDeploy(this::onAppUploadReceived);

            initialized = true;
        }
    }

    private void onPublishResultReceived(ModulePublishResultMsg msg) {
        log.info("Publish status: " + msg);
        moduleService.updatePublishStatus(msg.getMsgId(), msg.isSuccess(), msg.getMsg());
    }

    private void onAppUploadReceived(EcosAppDeployMsg msg) {
        try {
            ecosAppService.uploadApp(msg.getSource(), msg.getAppData());
        } catch (Exception e) {
            log.error("Application can't be uploaded", e);
        }
    }

    @Autowired(required = false)
    public void setApiFactory(EcosAppsApiFactory apiFactory) {
        this.apiFactory = apiFactory;
    }
}
