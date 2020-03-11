package ru.citeck.ecos.apps.app.module;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EcosAppsListener {

/*
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
            apiFactory.getModuleApi().onModuleDeleteResult(this::onDeleteResultReceived);
            apiFactory.getAppApi().onAppDeploy(this::onAppUploadReceived);

            initialized = true;
        }
    }

    private void onDeleteResultReceived(EcosModuleResultMsg msg) {
        log.info("Delete status: " + msg);
        if (msg.isSuccess() && msg.getModuleRef() != null) {
            moduleService.delete(msg.getModuleRef());
        }
    }

    private void onPublishResultReceived(EcosModuleResultMsg msg) {
        log.info("Publish status: " + msg);
        moduleService.updatePublishStatus(msg.getMsgId(), msg.isSuccess(), msg.getMsg());
        ecosAppService.updateAppsPublishStatus(msg.getModuleRef());
    }

    private void onAppUploadReceived(EcosAppDeployMsg msg) {
        try {
            ecosAppService.uploadApp(msg.getSource(), msg.getAppData(), PublishPolicy.PUBLISH_IF_NOT_PUBLISHED);
        } catch (Exception e) {
            log.error("Application can't be uploaded", e);
        }
    }

    @Autowired(required = false)
    public void setApiFactory(EcosAppsApiFactory apiFactory) {
        this.apiFactory = apiFactory;
    }
*/
}
