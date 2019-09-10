package ru.citeck.ecos.apps.web.rest;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import ru.citeck.ecos.apps.app.application.AppsAutoUpload;
import ru.citeck.ecos.apps.app.application.EcosAppService;
import ru.citeck.ecos.apps.app.module.EcosModuleService;
import ru.citeck.ecos.apps.module.type.DataType;
import ru.citeck.ecos.apps.module.type.EcosModule;
import ru.citeck.ecos.apps.module.type.EcosModuleRev;
import ru.citeck.ecos.records2.utils.MandatoryParam;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Controller for EcosApplication
 */
@Slf4j
@RestController
@RequestMapping("/app")
public class EcosAppController {

    private EcosModuleService moduleService;
    private AppsAutoUpload appsAutoUpload;
    private EcosAppService appService;

    public EcosAppController(EcosModuleService moduleService,
                             AppsAutoUpload appsAutoUpload,
                             EcosAppService appService) {
        this.appService = appService;
        this.moduleService = moduleService;
        this.appsAutoUpload = appsAutoUpload;
    }

    @RequestMapping(method = RequestMethod.POST, value = "/module/upload")
    public void acceptData(HttpServletRequest request) throws IOException {

        String id = request.getParameter("id");
        String type = request.getParameter("type");


        MandatoryParam.check("id", id);
        MandatoryParam.check("type", type);

        byte[] data = IOUtils.toByteArray(request.getInputStream());

        throw new UnsupportedOperationException();

        //todo
        //appService.upload(module);
    }

    //public void post

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

    @Data
    private static class UploadModule implements EcosModule {
        private String id;
        private String type;
        private String name;
        private int modelVersion;
        private DataType dataType;
        private byte[] data;
    }
}
