package ru.citeck.ecos.apps.app.module;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.EcosAppsApiFactory;
import ru.citeck.ecos.apps.app.api.EcosAppDeployMsg;
import ru.citeck.ecos.apps.app.application.EcosAppService;
import ru.citeck.ecos.apps.app.module.api.ModulePublishResultMsg;

import javax.annotation.PostConstruct;

@Slf4j
@Component
public class EcosAppsListener {

    private EcosModuleService moduleService;
    private EcosAppService ecosAppService;

    private EcosAppsApiFactory apiFactory;

    public EcosAppsListener(EcosModuleService moduleService,
                            EcosAppService ecosAppService) {
        this.moduleService = moduleService;
        this.ecosAppService = ecosAppService;
    }

    @PostConstruct
    void init() {
        if (apiFactory == null) {
            log.warn("Api Factory is null");
            return;
        }
        apiFactory.getModuleApi().onModulePublishResult(this::onPublishResultReceived);
        apiFactory.getAppApi().onAppDeploy(this::onAppUploadReceived);
    }

    private void onPublishResultReceived(ModulePublishResultMsg msg) {
        log.info("Publish status: " + msg);
        moduleService.updatePublishStatus(msg.getRevId(), msg.isSuccess(), msg.getMsg());
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
