package ru.citeck.ecos.apps.web.rest;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import ru.citeck.ecos.apps.app.module.EcosModuleRev;
import ru.citeck.ecos.apps.app.module.EcosModuleService;

/**
 * Controller for EcosApplication
 */
@RestController
@RequestMapping("/app")
public class EcosAppController {

    private EcosModuleService moduleService;

    public EcosAppController(EcosModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @GetMapping("/module/rev/{moduleRevId}")
    public HttpEntity<byte[]> downloadModule(@PathVariable String moduleRevId) {
        return toDownloadHttpEntity(moduleService.getModuleRev(moduleRevId));
    }

    @GetMapping("/module/type/{type}/{moduleId}")
    public HttpEntity<byte[]> downloadModule(@PathVariable String type,
                                             @PathVariable String moduleId) {
        return toDownloadHttpEntity(moduleService.getLastModuleRev(type, moduleId));
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

    //todo: rename model to module
    /*@PostMapping("/model/deploy")
    public Map<String, String> deployModel(@RequestBody Model module) throws SQLException {

        MandatoryParam.check("type", module.type);
        MandatoryParam.check("data", module.data);
        MandatoryParam.check("key", module.key);
        MandatoryParam.check("name", module.name);
        MandatoryParam.check("mimetype", module.mimetype);

        EcosModuleEntity ecosAppModel = new EcosModuleEntity();

        ecosAppModel.setType(module.type);
        //ecosAppModel.setKey(module.key);
        //ecosAppModel.setData(new SerialBlob(module.data));

        //ecosAppModel.setName(module.name);
        //ecosAppModel.setMimetype(module.mimetype);

        //applicationService.saveAndDeployModule(ecosAppModel);

        Map<String, String> result = new HashMap<>();
        result.put("status", "OK");

        return result;
    }*/

   /* private static class Model {

        public String key;
        public String name;
        public String type;
        public String mimetype;
        public byte[] data;
    }*/
}
