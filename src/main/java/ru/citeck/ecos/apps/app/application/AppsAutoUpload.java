package ru.citeck.ecos.apps.app.application;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.application.exceptions.ApplicationWithoutModules;
import ru.citeck.ecos.apps.app.application.exceptions.DowngrageIsNotSupported;

import javax.annotation.PostConstruct;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class AppsAutoUpload implements ApplicationListener<ApplicationReadyEvent> {

    private static final String APPS_PATTERN = "**.zip";
    private static final String SKIP_MSG = "Application was skipped: %s";
    private static final String APP_NAME = "ecosapps";

    @Value("${ecosapps.autoupload.locations:}")
    private String locations;

    private EcosAppReader reader;
    private EcosAppService appService;
    private EurekaClient eurekaClient;

    public AppsAutoUpload(EcosAppReader reader,
                          EcosAppService appService,
                          EurekaClient eurekaClient) {
        this.reader = reader;
        this.appService = appService;
        this.eurekaClient = eurekaClient;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {

        AtomicBoolean isReady = new AtomicBoolean(isReady());

        if (!isReady.get()) {

            long waitLimit = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2);

            new Thread(() -> {

                while (!isReady.get()) {

                    if (System.currentTimeMillis() > waitLimit) {
                        break;
                    }

                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    isReady.set(isReady());
                }

                if (isReady.get()) {
                    upload();
                } else {
                    throw new RuntimeException("Ready waiting timeout");
                }

            }).start();
        } else {
            upload();
        }
    }

    private boolean isReady() {
        try {
            return eurekaClient.getNextServerFromEureka(APP_NAME, false) != null;
        } catch (RuntimeException e) {
            //do nothing
        }
        return false;
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
                    appService.upload(reader.read(appPath.toFile()), true);
                } catch (ApplicationWithoutModules | DowngrageIsNotSupported e) {
                    log.warn(String.format(SKIP_MSG, e.getMessage()));
                }
            }
        }
    }
}
