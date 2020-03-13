package ru.citeck.ecos.apps.application;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import ru.citeck.ecos.apps.EcosAppsApp;
import ru.citeck.ecos.apps.TestUtils;
import ru.citeck.ecos.apps.app.*;
import ru.citeck.ecos.apps.app.application.EcosAppService;
import ru.citeck.ecos.apps.app.module.*;
import ru.citeck.ecos.commons.data.ObjectData;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@Slf4j
//@RunWith(SpringRunner.class)
//@SpringBootTest(classes = EcosAppsApp.class)
public class EcosAppPublishTest {

    /*private static final String DEPENDENT_APP_ID = "dependent-app";
    private static final String DEPENDANT_MODULE_ID = "test-form-in-dependent-app";

    @Autowired
    private EcosModuleService moduleService;

    @Autowired
    private EappsModuleService eappsModuleService;

    @Autowired
    private EcosAppService ecosAppService;

    @Autowired
    private EappsFactory factory;

    @Autowired
    private EcosAppsApiFactory eappsApi;

    private final List<EcosModule> deployedModules = new CopyOnWriteArrayList<>();

    @Before
    public void before() {
        factory.getModuleTypesRegistry().getAll().forEach(def -> {
            @SuppressWarnings("unchecked")
            Class<EcosModule> type = (Class<EcosModule>) def.getType();
            eappsApi.getModuleApi().onModulePublished(type, this::onModulePublished);
        });
    }

    private void onModulePublished(EcosModule module) {
        synchronized (deployedModules) {
            deployedModules.add(module);
        }
    }

    private boolean isPublishStatus(EcosApp app, PublishStatus status) {
        return status.equals(ecosAppService.getDeployStatus(app.getId()));
    }

    private boolean isPublishStatus(EcosModule module, PublishStatus status) {
        String typeId = eappsModuleService.getTypeId(module.getClass());
        return status.equals(moduleService.getDeployStatus(ModuleRef.create(typeId, module.getId())));
    }

    private boolean isPublishing(EcosApp app) {
        return isPublishStatus(app, PublishStatus.PUBLISHING);
    }

    public boolean isDepsWaiting(EcosApp app) {
        return isPublishStatus(app, PublishStatus.DEPS_WAITING);
    }

    public boolean isPublished(EcosApp app) {
        return isPublishStatus(app, PublishStatus.DEPLOYED);
    }

    public boolean isPublishFailed(EcosApp app) {
        return isPublishStatus(app, PublishStatus.PUBLISH_FAILED);
    }

    public boolean isPublishing(EcosModule module) {
        return isPublishStatus(module, PublishStatus.PUBLISHING);
    }

    @Test
    public void test() {

        EcosAppImpl baseApp = createBaseApp();

        synchronized (deployedModules) {
            ecosAppService.uploadApp("test", baseApp, PublishPolicy.PUBLISH_IF_NOT_PUBLISHED);
            assertTrue(isDepsWaiting(baseApp));
        }

        TestUtils.assertTrueWhile(() -> isDepsWaiting(baseApp), 1);

        assertThat(deployedModules.size(), is(0));

        EcosApp dependantApp = createDependentApp();

        synchronized (deployedModules) {
            ecosAppService.uploadApp("test", dependantApp, PublishPolicy.PUBLISH_IF_NOT_PUBLISHED);
            assertTrue(isPublishing(dependantApp));
            assertTrue(isDepsWaiting(baseApp));
            assertTrue(isPublishing(dependantApp.getModules().get(0)));
        }

        TestUtils.waitWhile(() -> isPublishing(dependantApp.getModules().get(0)), 5);
        TestUtils.waitWhile(() -> !isPublished(dependantApp), 5);
        TestUtils.waitWhile(() -> !isPublished(baseApp), 5);

        assertThat(deployedModules.size(), is(baseApp.getModules().size() + dependantApp.getModules().size()));

        deployedModules.forEach(m -> {

            String typeId = eappsModuleService.getTypeId(m.getClass());
            PublishStatus status = moduleService.getDeployStatus(ModuleRef.create(typeId, m.getId()));

            assertThat(status, is(PublishStatus.DEPLOYED));
        });

        Set<EcosModule> modulesFromApps = new HashSet<>(baseApp.getModules());
        modulesFromApps.addAll(dependantApp.getModules());

        Set<EcosModule> deployedModules = new HashSet<>(this.deployedModules);

        assertThat(deployedModules, is(modulesFromApps));

        synchronized (this.deployedModules) {

            ecosAppService.uploadApp("test", baseApp, PublishPolicy.PUBLISH_IF_NOT_PUBLISHED);

            PublishStatus publishStatus = ecosAppService.getDeployStatus(baseApp.getId());
            assertThat(publishStatus, is(PublishStatus.DEPLOYED));
        }

        FormModule form = new FormModule();
        form.setId("test-add-new-form");
        form.setFormKey("123");

        EcosAppImpl baseApp2 = new EcosAppImpl(baseApp);
        baseApp2.addModule(form);

        synchronized (this.deployedModules) {
            ecosAppService.uploadApp("test", baseApp2, PublishPolicy.PUBLISH_IF_NOT_PUBLISHED);
            assertTrue(isPublishing(baseApp2));
        }

        TestUtils.waitWhile(() -> isPublishing(baseApp2), 5);
        assertTrue(isPublished(baseApp2));

        modulesFromApps = new HashSet<>(baseApp2.getModules());
        modulesFromApps.addAll(dependantApp.getModules());

        deployedModules = new HashSet<>(this.deployedModules);
        assertThat(deployedModules.size(), is(this.deployedModules.size()));

        assertThat(deployedModules.size(), is(modulesFromApps.size()));
        assertThat(deployedModules, is(modulesFromApps));
    }

    private EcosAppImpl createBaseApp() {

        List<EcosModule> modules = new ArrayList<>();

        FormModule form = new FormModule();
        form.setId("form-0");
        form.setDefinition(new ObjectData());
        form.setTitle("Form title 0");
        modules.add(form);

        form = new FormModule();
        form.setId("form-1");
        form.setDefinition(new ObjectData());
        form.setTitle("Form title 1");
        modules.add(form);

        DashboardModule dashboardModule = new DashboardModule();
        dashboardModule.setId("dashboard-0");
        dashboardModule.setTypeRef(ModuleRef.create("type", "type0"));
        modules.add(dashboardModule);

        dashboardModule = new DashboardModule();
        dashboardModule.setId("dashboard-1");
        dashboardModule.setTypeRef(ModuleRef.create("type", "type2"));
        modules.add(dashboardModule);

        EcosAppImpl app = new EcosAppImpl();
        app.setId("test-app");
        app.setVersion(new EcosAppVersion("1.0.0"));
        app.setName("Test app name");
        app.addDependency(DEPENDENT_APP_ID, "*");
        app.setModules(modules);
        return app;
    }

    private EcosApp createDependentApp() {

        EcosAppImpl app = new EcosAppImpl();
        app.setId(DEPENDENT_APP_ID);
        app.setVersion(new EcosAppVersion("1.0.0"));
        app.setName("Test dependent name");

        FormModule module = new FormModule();
        module.setId(DEPENDANT_MODULE_ID);

        app.addModule(module);

        return app;
    }*/
}
