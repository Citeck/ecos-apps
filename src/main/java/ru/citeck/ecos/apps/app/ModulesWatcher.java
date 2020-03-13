package ru.citeck.ecos.apps.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.EcosAppsModuleTypeService;
import ru.citeck.ecos.apps.app.module.EcosModuleService;
import ru.citeck.ecos.apps.app.remote.AppStatus;
import ru.citeck.ecos.apps.module.local.LocalModulesService;
import ru.citeck.ecos.apps.module.remote.EcosAppInfo;
import ru.citeck.ecos.apps.module.remote.RemoteModulesService;
import ru.citeck.ecos.apps.module.type.TypeContext;
import ru.citeck.ecos.commons.io.file.EcosFile;

import java.time.Instant;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModulesWatcher {

    private static final long UNHEALTHY_APP_TTL = 10_000L;

    private final RemoteModulesService remoteModulesService;
    private final EcosAppsService ecosAppsService;
    private final LocalModulesService localModulesService;
    private final EcosModuleService ecosModuleService;

    private final EcosAppsModuleTypeService appsModuleTypeService;

    private boolean started = false;

    private Throwable lastError;
    private long errorNextPrintTime = 0L;

    private Map<String, AppStatusInfo> currentStatuses = new HashMap<>();

    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {

        if (!started) {

            log.info("ModulesWatcher initialization");

            Thread watcherThread = new Thread(this::runWatcher, "ModulesWatcher");
            watcherThread.start();
            started = true;
        }
    }

    private void handleNewApp(AppStatus newApp) {

        log.info("Detected new application '" + newApp.getAppName() + "' with EcosApps: " + newApp.getEcosApps());

        EcosFile newAppModuleTypesDir = remoteModulesService.getModuleTypesDir(newApp.getAppName());
        appsModuleTypeService.registerTypes(newApp.getAppName(), newAppModuleTypesDir);

        // get modules by new from all

        for (AppStatusInfo registeredApp : currentStatuses.values()) {
            loadModules(registeredApp.status.getAppName(), newApp.getAppName());
        }

        currentStatuses.put(newApp.getAppName(), new AppStatusInfo(newApp, Instant.now().toEpochMilli()));

        // get modules by all from new

        for (AppStatusInfo registeredApp : currentStatuses.values()) {
            loadModules(newApp.getAppName(), registeredApp.status.getAppName());
        }
    }

    private void loadModules(String fromApp, String toApp) {

        AppStatus fromStatus = currentStatuses.get(fromApp).status;

        List<TypeContext> toTypes = appsModuleTypeService.getTypesByAppName(toApp);
        if (toTypes.isEmpty() || fromStatus.getEcosApps().isEmpty()) {
            return;
        }

        EcosFile toTypesDir = appsModuleTypeService.getTypesDirByApp(toApp);

        for (EcosAppInfo ecosApp : fromStatus.getEcosApps()) {

            EcosFile modulesDir = remoteModulesService.getModulesDir(fromApp, ecosApp.getId(), toTypesDir);

            for (TypeContext typeCtx : toTypes) {

                List<Object> modules = localModulesService.readModulesForType(modulesDir, typeCtx.getId());

                log.info("Loaded " + modules.size() + " modules from '" + fromApp
                    + "' EcosApp: '" + ecosApp.getId() + "' type: '" + typeCtx.getId() + "'");

                ecosModuleService.uploadModules(fromApp, modules, typeCtx.getId());
            }
        }
    }

    private void runWatcher() {

        try {
            Thread.sleep(10_000);
        } catch (InterruptedException e) {
            log.error("Error", e);
        }

        while (true) {

            try {

                List<AppStatus> remoteStatus = ecosAppsService.getRemoteAppsStatus();
                Map<String, AppStatus> newApps = new HashMap<>();
                Set<String> missingApps = new HashSet<>(currentStatuses.keySet());

                remoteStatus.forEach(it -> {
                    AppStatusInfo currentStatus = currentStatuses.get(it.getAppName());
                    if (currentStatus == null) {
                        newApps.put(it.getAppName(), it);
                    } else {
                        List<EcosAppInfo> currentEcosApps = currentStatus.getStatus().getEcosApps();
                        if (it.getEcosApps().size() != currentEcosApps.size()) {
                            currentStatuses.remove(it.getAppName());
                            newApps.put(it.getAppName(), it);
                        }
                    }
                    missingApps.remove(it.getAppName());
                });

                List<String> apps = new ArrayList<>(currentStatuses.keySet());

                for (String appName : apps) {

                    AppStatusInfo appStatusInfo = currentStatuses.get(appName);

                    if (missingApps.contains(appName)) {

                        long lastHealthTime = appStatusInfo.lastPassedHealthCheck;

                        if (System.currentTimeMillis() - lastHealthTime > UNHEALTHY_APP_TTL) {
                            currentStatuses.remove(appName);
                            log.info("App '" + appName + "' was removed from registry because it " +
                                     "doesn't respond " + UNHEALTHY_APP_TTL + " ms");
                        }
                    } else {
                        appStatusInfo.lastPassedHealthCheck = System.currentTimeMillis();
                    }
                }

                newApps.values().forEach(this::handleNewApp);

                lastError = null;
                errorNextPrintTime = 0L;

                Thread.sleep(5_000);

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
                    Thread.sleep(10_000);
                } catch (Exception ex) {
                    log.error("Exception handler failed", ex);
                }
            }
        }
    }

    @Data
    @AllArgsConstructor
    private static class AppStatusInfo {
        private AppStatus status;
        private Long lastPassedHealthCheck;
    }
}
