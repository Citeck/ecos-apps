package ru.citeck.ecos.apps.domain.artifact.application.job;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.application.props.ApplicationProperties;
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceInfo;
import ru.citeck.ecos.apps.app.service.remote.RemoteAppService;
import ru.citeck.ecos.apps.app.service.remote.RemoteAppStatus;
import ru.citeck.ecos.apps.domain.artifact.application.service.EcosApplicationsService;
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService;
import ru.citeck.ecos.apps.domain.artifact.source.service.EcosArtifactsSourcesService;
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationsWatcherJob {

    private static final long UNHEALTHY_APP_TTL = Duration.ofSeconds(30).toMillis();
    private static final long CHECK_STATUS_PERIOD = Duration.ofSeconds(5).toMillis();
    private static final int HEALTH_CHECK_PROTECTION = (int) (UNHEALTHY_APP_TTL / CHECK_STATUS_PERIOD);

    private static final long SOURCES_LAST_SYNC_MAX_TIME = Duration.ofMinutes(10).toMillis();
    private static final long SOURCES_UPDATE_THRESHOLD_TIME = Duration.ofSeconds(3).toMillis();

    private static final long ARTIFACTS_LAST_DEPLOY_MAX_TIME = Duration.ofMinutes(10).toMillis();
    private static final long ARTIFACTS_DEPLOY_THRESHOLD_TIME = Duration.ofSeconds(3).toMillis();

    private final EcosApplicationsService ecosApplicationsService;
    private final EcosArtifactsSourcesService ecosArtifactsSourcesService;
    private final EcosArtifactTypesService ecosArtifactTypesService;
    private final EcosArtifactsService ecosArtifactsService;

    private final RemoteAppService remoteAppService;
    private final ApplicationProperties props;

    private boolean started = false;

    private Throwable lastError;
    private long errorNextPrintTime = 0L;

    private long sourcesLastSyncTime = System.currentTimeMillis();
    private long sourcesLastModifiedTime = 0;
    private long artifactsLastDeployTime = System.currentTimeMillis();
    private long artifactsLastModifiedTime = 0;

    private boolean forceUpdate = false;

    private final Map<String, AppStatusInfo> currentStatuses = new ConcurrentHashMap<>();

    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {

        if (!started) {
            Thread watcherThread = new Thread(this::runWatcher, "ArtifactsWatcher");
            watcherThread.start();
            started = true;
        }
    }

    private void runWatcher() {

        long initDelay = props.getModulesWatcher().getInitDelayMs();
        log.info("======= ApplicationsWatcherJob init sleep: " + initDelay + " =======");
        try {
            Thread.sleep(initDelay);
        } catch (InterruptedException e) {
            log.error("Error", e);
        }

        while (true) {

            try {

                boolean forceUpdate = this.forceUpdate;
                this.forceUpdate = false;

                updateAllApps();
                updateArtifacts(forceUpdate);

                long waitingStart = System.currentTimeMillis();
                while (!this.forceUpdate && (System.currentTimeMillis() - waitingStart) < CHECK_STATUS_PERIOD) {
                    Thread.sleep(100);
                }

            } catch (Throwable e) {
                try {
                    if (lastError == null || !Arrays.equals(lastError.getStackTrace(), e.getStackTrace())) {
                        log.error("Watcher error", e);
                        lastError = e;
                    } else {
                        if (errorNextPrintTime - System.currentTimeMillis() <= 0) {
                            log.error("Watcher error", e);
                            errorNextPrintTime = System.currentTimeMillis() + 60_000;
                        }
                    }
                    Thread.sleep(2 * CHECK_STATUS_PERIOD);
                } catch (Exception ex) {
                    log.error("Exception handler failed", ex);
                }
            }
        }
    }

    public void forceUpdate(String appInstanceId, ArtifactSourceInfo source) {
        log.info("Force update from " + appInstanceId + " " + source);
        forceUpdate = true;
    }

    @Synchronized
    private void updateAllApps() {

        List<RemoteAppStatus> remoteStatuses = remoteAppService.getAppsStatus();
        Map<String, RemoteAppStatus> newStatuses = new HashMap<>();

        remoteStatuses.forEach(status -> newStatuses.put(status.getAppInstanceId(), status));

        currentStatuses.forEach((instanceId, status) -> {
            RemoteAppStatus newStatus = newStatuses.get(instanceId);
            if (newStatus == null) {
                if (--status.healthCheckProtection <= 0) {
                    currentStatuses.remove(instanceId);
                }
            } else {
                status.status = newStatus;
                status.healthCheckProtection = HEALTH_CHECK_PROTECTION;
            }
        });

        newStatuses.forEach((instanceId, status) -> {
            if (!currentStatuses.containsKey(instanceId)) {
                currentStatuses.put(instanceId, new AppStatusInfo(status, HEALTH_CHECK_PROTECTION));
            }
        });

        ecosApplicationsService.updateApps(currentStatuses.values()
            .stream()
            .map(s -> s.status)
            .collect(Collectors.toList()));
    }

    @Synchronized
    private void updateArtifacts(boolean withoutThreshold) {

        long currentTime = System.currentTimeMillis();
        long sourcesOrTypesLastModified = Math.max(
            ecosArtifactsSourcesService.getLastModified().toEpochMilli(),
            ecosArtifactTypesService.getLastModified().toEpochMilli()
        );

        if (sourcesOrTypesLastModified > sourcesLastModifiedTime) {

            if (withoutThreshold || currentTime - sourcesOrTypesLastModified > SOURCES_UPDATE_THRESHOLD_TIME) {
                ecosArtifactsSourcesService.uploadArtifacts();
                sourcesLastSyncTime = currentTime;
                sourcesLastModifiedTime = sourcesOrTypesLastModified;
            }
        } else if (currentTime - sourcesLastSyncTime > SOURCES_LAST_SYNC_MAX_TIME) {

            ecosArtifactsSourcesService.uploadArtifacts();
            sourcesLastSyncTime = currentTime;
        }

        long artifactsLastModified = ecosArtifactsService.getLastModifiedTime().toEpochMilli();

        if (artifactsLastModified > artifactsLastModifiedTime) {

            if (withoutThreshold || currentTime - artifactsLastModified > ARTIFACTS_DEPLOY_THRESHOLD_TIME) {
                ecosApplicationsService.deployArtifacts();
                artifactsLastDeployTime = currentTime;
                artifactsLastModifiedTime = artifactsLastModified;
            }
        } else if (currentTime - artifactsLastDeployTime > ARTIFACTS_LAST_DEPLOY_MAX_TIME) {

            ecosApplicationsService.deployArtifacts();
            artifactsLastDeployTime = currentTime;
        }

        ecosArtifactsService.updateFailedArtifacts();
    }

    @Data
    @AllArgsConstructor
    private static class AppStatusInfo {
        private RemoteAppStatus status;
        private int healthCheckProtection;
    }
}
