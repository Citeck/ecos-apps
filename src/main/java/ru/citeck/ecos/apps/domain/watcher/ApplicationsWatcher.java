package ru.citeck.ecos.apps.domain.watcher;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceInfo;
import ru.citeck.ecos.apps.app.domain.status.AppStatus;
import ru.citeck.ecos.apps.app.service.remote.RemoteAppService;
import ru.citeck.ecos.apps.app.service.remote.RemoteAppStatus;
import ru.citeck.ecos.apps.artifact.ArtifactRef;
import ru.citeck.ecos.apps.domain.artifacttype.service.EcosArtifactTypesService;
import ru.citeck.ecos.apps.domain.artifact.service.EcosArtifactsService;
import ru.citeck.ecos.apps.domain.artifactpatch.service.ArtifactPatchService;
import ru.citeck.ecos.apps.app.application.props.ApplicationProperties;
import ru.citeck.ecos.apps.domain.ecosapp.service.EcosAppArtifactService;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.io.file.EcosFile;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.commons.utils.ExceptionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationsWatcher {

    private static final long UNHEALTHY_APP_TTL = 30_000L;
    private static final long CHECK_STATUS_PERIOD = 5_000L;
    private static final int HEALTH_CHECK_PROTECTION = (int) (UNHEALTHY_APP_TTL / CHECK_STATUS_PERIOD);

    private static final int COMPUTED_ARTIFACTS_REQUEST_LIMIT = 300;

    private final EcosArtifactsService ecosArtifactsService;
    private final ArtifactPatchService artifactPatchService;
    private final EcosAppArtifactService ecosAppArtifactService;
    private final EcosArtifactTypesService appsModuleTypeService;
    private final RemoteAppService remoteAppService;

    private final ApplicationProperties props;

    private boolean started = false;

    private Throwable lastError;
    private long errorNextPrintTime = 0L;

    private final Map<String, AppStatusInfo> currentStatuses = new ConcurrentHashMap<>();
    private final Queue<ArtifactRef> artifactsToUpdate = new ConcurrentLinkedQueue<>();

    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {

        if (!started) {

            log.info("======= ApplicationsWatcher Initialization =======");

            artifactPatchService.addListener(patch -> artifactsToUpdate.add(patch.getTarget()));

            Thread watcherThread = new Thread(this::runWatcher, "ArtifactsWatcher");
            watcherThread.start();
            started = true;
        }
    }

    private void handleNewApp(RemoteAppStatus newApp) {

        /*log.info("Detected new application '" + newApp.getAppName() + "' with sources: "
            + newApp.getStatus()
                .getSources()
                .stream()
                .map(ArtifactSourceInfo::getId)
                .collect(Collectors.toList())
        );

        EcosFile newAppModuleTypesDir = remoteModulesService.getModuleTypesDir(newApp.getAppName());
        appsModuleTypeService.registerTypes(newApp.getAppName(), newAppModuleTypesDir);

        // get modules by new from all

        for (AppStatusInfo registeredApp : currentStatuses.values()) {
            loadModules(registeredApp.status.getAppName(), newApp.getAppName());
        }

        currentStatuses.put(newApp.getAppName(), new AppStatusInfo(newApp, HEALTH_CHECK_PROTECTION));
        Consumer<Exception> exceptionHandler = e -> {
            // Something went wrong. Forget new app and wait until watcher find it again
            currentStatuses.remove(newApp.getAppName());
            ExceptionUtils.throwException(e);
        };


        // get modules by all from new

        try {
            for (AppStatusInfo registeredApp : currentStatuses.values()) {
                loadModules(newApp.getAppName(), registeredApp.status.getAppName());
            }
        } catch (Exception e) {
            exceptionHandler.accept(e);
        }

        try {
            List<TypeContext> types = appsModuleTypeService.getTypesByAppName(newApp.getAppName());
            ecosAppArtifactService.uploadAllArtifactsForTypes(types);
        } catch (Exception e) {
            exceptionHandler.accept(e);
        }*/
    }

    private void loadModules(String fromApp, String toApp) {

        /*if (log.isDebugEnabled()) {
            log.debug("Load modules from: " + fromApp + " to app: " + toApp);
        }

        AppStatus fromStatus = currentStatuses.get(fromApp).status;

        List<TypeContext> toTypes = appsModuleTypeService.getTypesByAppName(toApp);
        if (toTypes.isEmpty() || fromStatus.getEcosApps().isEmpty()) {
            return;
        }

        EcosFile toTypesDir = appsModuleTypeService.getTypesDirByApp(toApp);

        for (EcosAppInfo ecosApp : fromStatus.getEcosApps()) {

            Set<String> providedTypes = ecosApp.getProvidedTypes();
            if (providedTypes != null) {
                if (toTypes.stream().noneMatch(t -> providedTypes.contains(t.getId()))) {
                    continue;
                }
            }

            int iterations = 0;

            while (true) {

                String ecosAppId = ecosApp.getId();
                if (log.isDebugEnabled()) {
                    log.debug("Get app artifact types meta '" + ecosAppId + "' " + toTypes.stream()
                                 .map(TypeContext::getId)
                                 .collect(Collectors.joining(", ")));
                }
                Map<String, AppModuleTypeMeta> typeMeta = ecosAppService.getAppModuleTypesMeta(ecosAppId, toTypes);
                Map<String, GetModulesMeta> getModulesMeta = new HashMap<>();

                typeMeta.forEach((type, module) ->
                    getModulesMeta.put(type, new GetModulesMeta(module.getLastConsumedMs(), ObjectData.create()))
                );

                EcosFile modulesDir = remoteModulesService.getModulesDir(
                    fromApp,
                    ecosApp.getId(),
                    ecosApp.getType(),
                    toTypesDir,
                    getModulesMeta
                );

                for (TypeContext typeCtx : toTypes) {

                    if (providedTypes != null && !providedTypes.contains(typeCtx.getId())) {
                        continue;
                    }

                    List<Object> modules = localModulesService.readModulesForType(modulesDir, typeCtx.getId());

                    if (iterations == 0 || modules.size() > 0) {
                        log.info("Loaded " + modules.size() + " modules from '" + fromApp
                            + "'->'" + ecosApp.getId() + "' type: '" + typeCtx.getId() + "'");
                    }

                    ecosArtifactsService.uploadEcosAppArtifacts(fromApp, modules, typeCtx.getId(), null);
                }

                EcosFile computedMeta = modulesDir.getFile(EcosAppConstants.COMPUTED_META_FILE);
                if (computedMeta != null) {
                    ComputedMetaByType metaByType = Json.getMapper().read(
                        computedMeta.readAsBytes(),
                        ComputedMetaByType.class
                    );
                    if (ecosAppService.updateAppModuleTypesMeta(ecosApp.getId(), metaByType)) {
                        if (++iterations > 300) {
                            log.warn("Computed modules request limit was reached: "
                                + COMPUTED_ARTIFACTS_REQUEST_LIMIT + ". Loading will be interrupted");
                        } else {
                            continue;
                        }
                    }
                }
                break;
            }
        }*/
    }

    private void runWatcher() {

        /*long initDelay = props.getModulesWatcher().getInitDelayMs();
        log.info("ArtifactsWatcher init sleep: " + initDelay);
        try {
            Thread.sleep(initDelay);
        } catch (InterruptedException e) {
            log.error("Error", e);
        }

        while (true) {

            try {

                List<RemoteAppStatus> remoteStatus = remoteAppService.getAppsStatus();
                Map<String, RemoteAppStatus> newApps = new HashMap<>();
                Set<String> missingApps = new HashSet<>(currentStatuses.keySet());

                remoteStatus.forEach(it -> {

                    AppStatusInfo currentStatus = currentStatuses.get(it.getAppName());

                    if (currentStatus == null) {

                        newApps.put(it.getAppName(), it);

                    } else {

                        List<ArtifactSourceInfo> currentSources = currentStatus.getStatus().getSources();

                        if (it.getStatus().getSources().size() != currentSources.size()) {

                            currentStatuses.remove(it.getAppName());
                            newApps.put(it.getAppName(), it);

                        } else if (it.getStarted() > currentStatus.getStatus().getStarted()) {

                            AppStatus existingNewStatus = newApps.get(it.getAppName());
                            if (existingNewStatus == null || existingNewStatus.getStarted() < it.getStarted()) {
                                currentStatuses.remove(it.getAppName());
                                newApps.put(it.getAppName(), it);
                            }
                        }
                    }
                    missingApps.remove(it.getAppName());
                });

                List<String> apps = new ArrayList<>(currentStatuses.keySet());

                for (String appName : apps) {

                    AppStatusInfo appStatusInfo = currentStatuses.get(appName);

                    if (missingApps.contains(appName)) {

                        if (--appStatusInfo.healthCheckProtection <= 0) {
                            currentStatuses.remove(appName);
                            log.info("App '" + appName + "' was removed from registry because it " +
                                     "doesn't respond " + HEALTH_CHECK_PROTECTION + " times");
                        }
                    } else {
                        appStatusInfo.healthCheckProtection = HEALTH_CHECK_PROTECTION;
                    }
                }

                newApps.values().forEach(this::handleNewApp);

                lastError = null;
                errorNextPrintTime = 0L;

                Set<ArtifactRef> modulesToUpdateSet = new HashSet<>();
                ArtifactRef moduleToUpdate = artifactsToUpdate.poll();
                while (moduleToUpdate != null) {
                    modulesToUpdateSet.add(moduleToUpdate);
                    moduleToUpdate = artifactsToUpdate.poll();
                }

                for (ArtifactRef artifactRef : modulesToUpdateSet) {
                    ecosArtifactsService.updateModule(artifactRef);
                }

                Thread.sleep(CHECK_STATUS_PERIOD);

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
        }*/
    }

    @Data
    @AllArgsConstructor
    private static class AppStatusInfo {
        private AppStatus status;
        private int healthCheckProtection;
    }
}
