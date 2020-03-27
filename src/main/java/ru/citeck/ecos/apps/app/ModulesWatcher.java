package ru.citeck.ecos.apps.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.application.AppModuleTypeMeta;
import ru.citeck.ecos.apps.app.application.EcosAppService;
import ru.citeck.ecos.apps.app.module.EcosAppsModuleTypeService;
import ru.citeck.ecos.apps.app.module.EcosModuleService;
import ru.citeck.ecos.apps.app.provider.ComputedMeta;
import ru.citeck.ecos.apps.app.remote.AppStatus;
import ru.citeck.ecos.apps.module.command.getmodules.GetModulesMeta;
import ru.citeck.ecos.apps.module.local.LocalModulesService;
import ru.citeck.ecos.apps.module.remote.EcosAppInfo;
import ru.citeck.ecos.apps.module.remote.RemoteModulesService;
import ru.citeck.ecos.apps.module.type.TypeContext;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.io.file.EcosFile;
import ru.citeck.ecos.commons.json.Json;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModulesWatcher {

    private static final long UNHEALTHY_APP_TTL = 10_000L;
    private static final long CHECK_STATUS_PERIOD = 5_000L;
    private static final int HEALTH_CHECK_PROTECTION = (int) (UNHEALTHY_APP_TTL / CHECK_STATUS_PERIOD);

    private static final int COMPUTED_MODULES_REQUEST_LIMIT = 300;

    private final RemoteModulesService remoteModulesService;
    private final EcosAppsService ecosAppsService;
    private final LocalModulesService localModulesService;
    private final EcosModuleService ecosModuleService;

    private final EcosAppService ecosAppService;

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

        currentStatuses.put(newApp.getAppName(), new AppStatusInfo(newApp, HEALTH_CHECK_PROTECTION));

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

            Set<String> providedTypes = ecosApp.getProvidedTypes();
            if (providedTypes != null) {
                if (toTypes.stream().noneMatch(t -> providedTypes.contains(t.getId()))) {
                    continue;
                }
            }

            int iterations = 0;

            while (true) {

                String ecosAppId = ecosApp.getId();
                Map<String, AppModuleTypeMeta> typeMeta = ecosAppService.getAppModuleTypesMeta(ecosAppId, toTypes);
                Map<String, GetModulesMeta> getModulesMeta = new HashMap<>();

                typeMeta.forEach((type, module) ->
                    getModulesMeta.put(type, new GetModulesMeta(module.getLastConsumedMs(), new ObjectData()))
                );

                EcosFile modulesDir = remoteModulesService.getModulesDir(
                    fromApp,
                    ecosApp.getId(),
                    ecosApp.getType(),
                    toTypesDir,
                    getModulesMeta
                );

                for (TypeContext typeCtx : toTypes) {

                    List<Object> modules = localModulesService.readModulesForType(modulesDir, typeCtx.getId());

                    if (iterations == 0 || modules.size() > 0) {
                        log.info("Loaded " + modules.size() + " modules from '" + fromApp
                            + "' EcosApp: '" + ecosApp.getId() + "' type: '" + typeCtx.getId() + "'");
                    }

                    ecosModuleService.uploadModules(fromApp, modules, typeCtx.getId());
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
                                + COMPUTED_MODULES_REQUEST_LIMIT + ". Loading will be interrupted");
                        } else {
                            continue;
                        }
                    }
                }
                break;
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
        }
    }

    @Data
    @AllArgsConstructor
    private static class AppStatusInfo {
        private AppStatus status;
        private int healthCheckProtection;
    }

    public static class ComputedMetaByType extends HashMap<String, List<ComputedMeta>> {}
}
