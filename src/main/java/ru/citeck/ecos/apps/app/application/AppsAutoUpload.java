package ru.citeck.ecos.apps.app.application;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.application.exceptions.ApplicationWithoutModules;
import ru.citeck.ecos.apps.app.application.exceptions.DowngrageIsNotSupported;

import javax.annotation.PostConstruct;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
public class AppsAutoUpload {

    private static final String APPS_PATTERN = "**.zip";
    private static final String SKIP_MSG = "Application was skipped: %s";

    @Value("${eapps.autoupload.locations:}")
    private String locations;

    private EcosAppService appService;

    public AppsAutoUpload(EcosAppService appService) {
        this.appService = appService;
    }

    @PostConstruct
    public void init() {
        upload();
    }

    public void upload() {
        log.info("================== APPS AUTO UPLOAD ==================");
        try {
            uploadImpl();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        log.info("================= /APPS AUTO UPLOAD ==================");
    }

    private void uploadImpl() throws URISyntaxException {

        if (StringUtils.isBlank(locations)) {
            log.info("Auto upload locations is not specified");
            return;
        }

        String[] locationsArr = locations.split(";");

        for (String locationStr : locationsArr) {

            log.info("Check location: " + locationStr);

            File locationFile = new File(new URI(locationStr));
            if (!locationFile.exists()) {
                log.info("Location doesn't exists: " + locationStr);
                continue;
            }

            List<Path> applications = FileUtils.findFiles(locationFile, APPS_PATTERN);

            log.info("Found " + applications.size() + " applications");

            for (Path appPath : applications) {

                log.info("Upload app: " + appPath);
                try {
                    appService.uploadApp(appPath.toFile());
                } catch (ApplicationWithoutModules | DowngrageIsNotSupported e) {
                    log.warn(String.format(SKIP_MSG, e.getMessage()));
                }
            }
        }
    }
}
