package ru.citeck.ecos.apps.web.rest;

import org.apache.commons.io.IOUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.citeck.ecos.apps.app.application.EcosAppService;
import ru.citeck.ecos.apps.app.module.EcosModuleRev;
import ru.citeck.ecos.apps.app.module.EcosModuleService;

import java.util.List;

@Component
@RestController
@RequestMapping("/api")
public class EcosAppController {

    private EcosModuleService moduleService;
    private EcosAppService appService;

    public EcosAppController(EcosModuleService moduleService,
                             EcosAppService appService) {
        this.moduleService = moduleService;
        this.appService = appService;
    }

    @GetMapping("/module/rev/{moduleRevId}")
    public HttpEntity<byte[]> downloadModule(@PathVariable String moduleRevId) {
        return toDownloadHttpEntity(moduleService.getModuleRevision(moduleRevId));
    }

    @GetMapping("/module/type/{type}/{moduleId}")
    public HttpEntity<byte[]> downloadModule(@PathVariable String type,
                                             @PathVariable String moduleId) {

        //return toDownloadHttpEntity(moduleService.getLastModuleRev(ModuleRef.create(type, moduleId)));
        return null;
    }

    @GetMapping("/module/all/publish")
    public String publishAllModules() {
        //moduleService.publishAllModules(true);
        return "OK";
    }

    @GetMapping("/module/type/{type}/{moduleId}/publish")
    public String publishModule(@PathVariable String type,
                                @PathVariable String moduleId) {

        //moduleService.publishModule(ModuleRef.create(type, moduleId), true);
        return "OK";
    }

    @GetMapping("/app/{appId}/publish")
    public String publishApp(@PathVariable String appId) {

        appService.publishApp(appId);
        return "OK";
    }

    private HttpEntity<byte[]> toDownloadHttpEntity(EcosModuleRev rev) {

        /*EappMemDir eappMemDir = EappZipUtils.extractZip(rev.getData());
        List<EappFileBase> files = eappMemDir.getChildren();

        byte[] data;
        String filename;

        if (files.size() == 1 && files.get(0) instanceof EappFile) {

            EappFile file = (EappFile) files.get(0);

            filename = file.getName();
            data = file.read(IOUtils::toByteArray);

        } else {

            filename = rev.getId() + ".zip";
            data = rev.getData();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
            ContentDisposition.builder("attachment")
                .filename(filename)
                .build());

        return new HttpEntity<>(data, headers);*/
        return null;
    }
}

