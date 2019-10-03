package ru.citeck.ecos.apps.web.rest;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.citeck.ecos.apps.app.application.AppsAutoUpload;
import ru.citeck.ecos.apps.app.application.EcosAppService;
import ru.citeck.ecos.apps.app.module.EcosModuleService;
import ru.citeck.ecos.apps.module.type.EcosModuleRev;

@Component
@RestController
@RequestMapping("/api")
public class EcosAppController {

    private EcosModuleService moduleService;
    private AppsAutoUpload appsAutoUpload;
    private EcosAppService appService;

    public EcosAppController(EcosModuleService moduleService,
                             AppsAutoUpload appsAutoUpload,
                             EcosAppService appService) {
        this.moduleService = moduleService;
        this.appsAutoUpload = appsAutoUpload;
        this.appService = appService;
    }

    @GetMapping("/module/rev/{moduleRevId}")
    public HttpEntity<byte[]> downloadModule(@PathVariable String moduleRevId) {
        return toDownloadHttpEntity(moduleService.getModuleRevision(moduleRevId));
    }

    @GetMapping("/module/type/{type}/{moduleId}")
    public HttpEntity<byte[]> downloadModule(@PathVariable String type,
                                             @PathVariable String moduleId) {

        return toDownloadHttpEntity(moduleService.getLastModuleRev(type, moduleId));
    }

    @GetMapping("/module/type/{type}/{moduleId}/publish")
    public String publishModule(@PathVariable String type,
                                @PathVariable String moduleId) {

        moduleService.publishModule(type, moduleId);
        return "OK";
    }

    @GetMapping("/app/{appId}/publish")
    public String publishApp(@PathVariable String appId) {

        appService.publishApp(appId);
        return "OK";
    }

    @GetMapping("/autoupload/update")
    public String updateAppsAutoUpload() {
        appsAutoUpload.upload();
        return "OK";
    }

    private HttpEntity<byte[]> toDownloadHttpEntity(EcosModuleRev rev) {

        String filename = rev.getId() + "." + rev.getDataType().getExt();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
            ContentDisposition.builder("attachment")
                .filename(filename)
                .build());

        return new HttpEntity<>(rev.getData(), headers);
    }
}

