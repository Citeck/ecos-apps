package ru.citeck.ecos.apps.app.application.api.rest;

import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.citeck.ecos.apps.domain.artifact.service.EcosArtifactRev;
import ru.citeck.ecos.apps.domain.artifact.service.EcosArtifactsService;

@Component
@RestController
@RequestMapping("/api")
public class EcosAppController {

    private EcosArtifactsService moduleService;

    public EcosAppController(EcosArtifactsService moduleService) {
        this.moduleService = moduleService;
    }

    @GetMapping("/module/type/{type}/{moduleId}")
    public HttpEntity<byte[]> downloadModule(@PathVariable String type,
                                             @PathVariable String moduleId) {

        //return toDownloadHttpEntity(moduleService.getLastModuleRev(ArtifactRef.create(type, moduleId)));
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

        //moduleService.publishModule(ArtifactRef.create(type, moduleId), true);
        return "OK";
    }

    private HttpEntity<byte[]> toDownloadHttpEntity(EcosArtifactRev rev) {

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

