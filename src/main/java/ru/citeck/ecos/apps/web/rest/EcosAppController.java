package ru.citeck.ecos.apps.web.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import ru.citeck.ecos.apps.app.application.AppsAutoUpload;
import ru.citeck.ecos.apps.app.module.EcosModuleService;
import ru.citeck.ecos.apps.module.type.EcosModuleRev;

/**
 * Controller for EcosApplication
 */
@Slf4j
@RestController
@RequestMapping("/app")
public class EcosAppController {

    private EcosModuleService moduleService;
    private AppsAutoUpload appsAutoUpload;

    public EcosAppController(EcosModuleService moduleService,
                             AppsAutoUpload appsAutoUpload) {
        this.moduleService = moduleService;
        this.appsAutoUpload = appsAutoUpload;
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

    @GetMapping("/autoupload/update")
    public String updateAppsAutoupload() {
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
