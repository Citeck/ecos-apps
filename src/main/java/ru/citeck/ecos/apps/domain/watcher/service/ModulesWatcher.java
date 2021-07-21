package ru.citeck.ecos.apps.domain.watcher.service;

import kotlin.Unit;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.EcosAppConstants;
import ru.citeck.ecos.apps.app.EcosAppsService;
import ru.citeck.ecos.apps.app.api.GetAppBuildInfoCommand;
import ru.citeck.ecos.apps.app.api.GetAppBuildInfoCommandResp;
import ru.citeck.ecos.apps.domain.application.service.AppModuleTypeMeta;
import ru.citeck.ecos.apps.domain.application.service.EcosAppService;
import ru.citeck.ecos.apps.domain.application.service.EcosAppsModuleTypeService;
import ru.citeck.ecos.apps.domain.buildinfo.api.records.BuildInfoRecords;
import ru.citeck.ecos.apps.domain.module.service.EcosModuleService;
import ru.citeck.ecos.apps.domain.modulepatch.service.ModulePatchService;
import ru.citeck.ecos.apps.app.provider.ComputedMeta;
import ru.citeck.ecos.apps.app.remote.AppStatus;
import ru.citeck.ecos.apps.app.application.props.ApplicationProperties;
import ru.citeck.ecos.apps.module.ModuleRef;
import ru.citeck.ecos.apps.module.command.getmodules.GetModulesMeta;
import ru.citeck.ecos.apps.module.local.LocalModulesService;
import ru.citeck.ecos.apps.module.remote.EcosAppInfo;
import ru.citeck.ecos.apps.module.remote.RemoteModulesService;
import ru.citeck.ecos.apps.module.type.TypeContext;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.commands.dto.CommandResult;
import ru.citeck.ecos.commands.utils.CommandUtils;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.io.file.EcosFile;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.commons.utils.ExceptionUtils;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModulesWatcher {

    private static final long UNHEALTHY_APP_TTL = 30_000L;
    private static final long CHECK_STATUS_PERIOD = 5_000L;
    private static final int HEALTH_CHECK_PROTECTION = (int) (UNHEALTHY_APP_TTL / CHECK_STATUS_PERIOD);

    private static final int COMPUTED_MODULES_REQUEST_LIMIT = 300;

    private final RemoteModulesService remoteModulesService;
    private final EcosAppsService ecosAppsService;
    private final LocalModulesService localModulesService;
    private final EcosModuleService ecosModuleService;
    private final ModulePatchService modulePatchService;
    private final EcosAppService ecosAppService;
    private final EcosAppsModuleTypeService appsModuleTypeService;

    private final ApplicationProperties props;

    private final BuildInfoRecords buildInfoRecords;
    private final CommandsService commandsService;

    private boolean started = false;

    private Throwable lastError;
    private long errorNextPrintTime = 0L;

    private final Map<String, AppStatusInfo> currentStatuses = new ConcurrentHashMap<>();
    private final Queue<ModuleRef> modulesToUpdate = new ConcurrentLinkedQueue<>();

    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {

        if (!started) {

            log.info("ModulesWatcher initialization");

            modulePatchService.addListener(patch -> modulesToUpdate.add(patch.getTarget()));

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

        try {
            for (AppStatusInfo registeredApp : currentStatuses.values()) {
                loadModules(newApp.getAppName(), registeredApp.status.getAppName());
            }
        } catch (Exception e) {
            // Something went wrong. Forget new app and wait until watcher find it again
            currentStatuses.remove(newApp.getAppName());
            ExceptionUtils.throwException(e);
        }

        String appFullName = newApp.getAppName() + " (" + newApp.getAppInstanceId() + ")";
        try {

            log.info("Send build info request to " + appFullName);

            CommandResult result = commandsService.execute(commandsService.buildCommand(b -> {
                b.setTargetApp(CommandUtils.INSTANCE.getTargetAppByAppInstanceId(newApp.getAppInstanceId()));
                b.setBody(new GetAppBuildInfoCommand(Instant.EPOCH));
                return Unit.INSTANCE;
            })).get(10, TimeUnit.SECONDS);
            result.throwPrimaryErrorIfNotNull();

            GetAppBuildInfoCommandResp resp = result.getResultAs(GetAppBuildInfoCommandResp.class);

            if (resp != null) {

                String info = "[" + resp.getBuildInfo().stream().map(b ->
                    "version: " + b.getVersion() +
                        " branch: " + b.getBranch() +
                        " buildDate: " + b.getBuildDate())
                    .collect(Collectors.joining(", ")) + "]";
                log.info("Register new build info for app " + appFullName + " " + info);

                buildInfoRecords.register(newApp, resp.getBuildInfo());

            } else {
                log.error("Build info is null for app " + appFullName);
            }

        } catch (Exception e) {
            log.error("Error in build info request for app " + appFullName, e);
        }
    }

    private void loadModules(String fromApp, String toApp) {

        if (log.isDebugEnabled()) {
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
                    log.debug("Get app module types meta '" + ecosAppId + "' "
                        + toTypes.stream().map(TypeContext::getId).collect(Collectors.joining(", ")));
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

        long initDelay = props.getModulesWatcher().getInitDelayMs();
        log.info("Modules watcher init sleep: " + initDelay);
        try {
            Thread.sleep(initDelay);
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

                Set<ModuleRef> modulesToUpdateSet = new HashSet<>();
                ModuleRef moduleToUpdate = modulesToUpdate.poll();
                while (moduleToUpdate != null) {
                    modulesToUpdateSet.add(moduleToUpdate);
                    moduleToUpdate = modulesToUpdate.poll();
                }

                for (ModuleRef moduleRef : modulesToUpdateSet) {
                    ecosModuleService.updateModule(moduleRef);
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
