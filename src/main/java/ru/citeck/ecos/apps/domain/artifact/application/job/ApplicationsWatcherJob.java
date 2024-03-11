package ru.citeck.ecos.apps.domain.artifact.application.job;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.application.props.ApplicationProperties;
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceInfo;
import ru.citeck.ecos.apps.app.service.remote.RemoteAppService;
import ru.citeck.ecos.apps.app.service.remote.RemoteAppStatus;
import ru.citeck.ecos.apps.domain.artifact.application.service.EcosApplicationsService;
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService;
import ru.citeck.ecos.apps.domain.artifact.patch.service.EcosArtifactsPatchService;
import ru.citeck.ecos.apps.domain.artifact.source.service.EcosArtifactsSourcesService;
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService;
import ru.citeck.ecos.commons.utils.ExceptionUtils;
import ru.citeck.ecos.micrometer.EcosMicrometerContext;
import ru.citeck.ecos.micrometer.obs.EcosObs;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationsWatcherJob {

    private static final long UNHEALTHY_APP_TTL = Duration.ofSeconds(30).toMillis();
    private static final long CHECK_STATUS_PERIOD = Duration.ofSeconds(5).toMillis();
    private static final int HEALTH_CHECK_PROTECTION = (int) (UNHEALTHY_APP_TTL / CHECK_STATUS_PERIOD);

    private static final long SOURCES_UPDATE_THRESHOLD_TIME = Duration.ofSeconds(3).toMillis();
    private static final long ARTIFACTS_DEPLOY_THRESHOLD_TIME = Duration.ofSeconds(3).toMillis();

    private final EcosApplicationsService ecosApplicationsService;
    private final EcosArtifactsSourcesService ecosArtifactsSourcesService;
    private final EcosArtifactTypesService ecosArtifactTypesService;
    private final EcosArtifactsService ecosArtifactsService;
    private final EcosArtifactsPatchService ecosArtifactsPatchService;
    private final EcosMicrometerContext ecosMicrometerContext;

    private final RemoteAppService remoteAppService;
    private final ApplicationProperties props;

    private boolean started = false;

    private Throwable lastError;
    private long errorNextPrintTime = 0L;

    private long sourcesOrTypesLastModifiedTime = 0;
    private long typesLastModifiedTime = 0;
    private long artifactsLastModifiedTime = 0;

    private boolean forceUpdate = false;
    private final AtomicReference<CompletableFuture<Boolean>> nextUpdateFuture = new AtomicReference<>();

    private final AtomicBoolean isContextClosed = new AtomicBoolean(false);
    private Thread watcherThread;

    private final Map<String, AppStatusInfo> currentStatuses = new ConcurrentHashMap<>();

    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {

        if (!started) {
            watcherThread = new Thread(this::runWatcher, "ArtifactsWatcher");
            watcherThread.start();
            started = true;
        }
    }

    public Set<String> getActiveApps() {
        Set<String> appNames = new HashSet<>();
        currentStatuses.values().forEach(it -> {
            String appName = it.getStatus().getAppName();
            if (StringUtils.isNotBlank(appName)) {
                appNames.add(appName);
            }
        });
        return appNames;
    }

    private void runWatcher() {

        long initDelay = props.getModulesWatcher().getInitDelayMs();
        log.info("======= ApplicationsWatcherJob init sleep: " + initDelay + " =======");
        try {
            Thread.sleep(initDelay);
        } catch (InterruptedException e) {
            log.error("Error", e);
        }

        while (!isContextClosed.get()) {
            doWatcherJob();
        }
    }

    @SneakyThrows
    @SuppressWarnings("BusyWait")
    private void doWatcherJob() {

        try {

            boolean forceUpdateChangedSources = this.forceUpdate;
            this.forceUpdate = false;

            EcosObs observation = ecosMicrometerContext.createObs("ecos.apps.watcher");

            observation.observeJ(() -> {
                if (!isContextClosed.get()) {
                    updateAllApps();
                }
                if (!isContextClosed.get()) {
                    updateArtifacts(forceUpdateChangedSources);
                }
            });

            if (isContextClosed.get()) {
                return;
            }

            long waitingStart = System.currentTimeMillis();
            while (!this.forceUpdate && (System.currentTimeMillis() - waitingStart) < CHECK_STATUS_PERIOD) {

                Thread.sleep(100);

                if (isContextClosed.get()) {
                    return;
                }
            }

        } catch (InterruptedException e) {
            log.info("Watcher thread was interrupted");
            Thread.currentThread().interrupt();
            throw e;
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

    public synchronized void forceUpdate() {
        forceUpdate = true;
    }

    public void forceUpdateSync() {
        CompletableFuture<Boolean> future;
        synchronized (this) {
            future = nextUpdateFuture.get();
            if (future == null) {
                future = new CompletableFuture<>();
                nextUpdateFuture.set(future);
            }
            forceUpdate = true;
        }
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            ExceptionUtils.throwException(e);
        }
    }

    public synchronized void forceUpdate(String appInstanceId, ArtifactSourceInfo source) {
        log.info("Force update from " + appInstanceId + " " + source);
        forceUpdate = true;
    }

    @Synchronized
    private void updateAllApps() {

        log.trace("All apps updating started");

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
    private void updateArtifacts(boolean forceUpdateChangedSources) {

        log.trace("Artufacts updating started");

        long currentTime = System.currentTimeMillis();
        long typesChangeTime = ecosArtifactTypesService.getLastModified().toEpochMilli();
        long sourcesChangeTime = ecosArtifactsSourcesService.getLastModified().toEpochMilli();

        long sourcesOrTypesLastModified = Math.max(sourcesChangeTime, typesChangeTime);
        boolean typesWasChanged = typesChangeTime > typesLastModifiedTime;

        if (sourcesOrTypesLastModified > sourcesOrTypesLastModifiedTime
                && (forceUpdateChangedSources
                    || (currentTime - sourcesOrTypesLastModified) > SOURCES_UPDATE_THRESHOLD_TIME)) {

            try {

                ecosArtifactsSourcesService.uploadArtifacts(!typesWasChanged);
                ecosArtifactsPatchService.applyOutOfSyncPatches();

                sourcesOrTypesLastModifiedTime = sourcesOrTypesLastModified;
                typesLastModifiedTime = typesChangeTime;
            } catch (Exception e) {
                log.error("Artifacts uploading error", e);
            }
        }

        Instant artifactsLastModified = ecosArtifactsService.getLastModifiedTime();
        long artifactsLastModifiedMs = artifactsLastModified.toEpochMilli();

        if (artifactsLastModifiedMs > artifactsLastModifiedTime
                && (forceUpdateChangedSources
                    || currentTime - artifactsLastModifiedMs > ARTIFACTS_DEPLOY_THRESHOLD_TIME)) {

            try {

                ecosApplicationsService.deployArtifacts(artifactsLastModified);
                if (ecosArtifactsPatchService.applyOutOfSyncPatches()) {
                    artifactsLastModified = ecosArtifactsService.getLastModifiedTime();
                    artifactsLastModifiedMs = artifactsLastModified.toEpochMilli();
                    ecosApplicationsService.deployArtifacts(artifactsLastModified);
                }

                artifactsLastModifiedTime = artifactsLastModifiedMs;

            } catch (Exception e) {
                log.error("Artifacts deployment error", e);
            }
        }

        ecosArtifactsService.updateFailedArtifacts();

        CompletableFuture<Boolean> nextUpdateFuture = this.nextUpdateFuture.getAndSet(null);
        if (nextUpdateFuture != null) {
            nextUpdateFuture.complete(true);
        }
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent event) throws InterruptedException {
        log.info("Context was closed and watcher will be terminated");
        isContextClosed.set(true);
        watcherThread.interrupt();
        watcherThread.join();
    }

    @Data
    @AllArgsConstructor
    private static class AppStatusInfo {
        private RemoteAppStatus status;
        private int healthCheckProtection;
    }
}
