package ru.citeck.ecos.apps.application;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import ru.citeck.ecos.apps.EappsFactory;
import ru.citeck.ecos.apps.EcosAppsApiFactory;
import ru.citeck.ecos.apps.EcosAppsApp;
import ru.citeck.ecos.apps.TestUtils;
import ru.citeck.ecos.apps.app.*;
import ru.citeck.ecos.apps.app.application.EcosAppService;
import ru.citeck.ecos.apps.app.module.*;
import ru.citeck.ecos.apps.app.module.type.dashboard.DashboardModule;
import ru.citeck.ecos.apps.app.module.type.form.FormModule;
import ru.citeck.ecos.apps.app.patch.EcosPatch;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = EcosAppsApp.class)
public class EcosAppPublishTest {

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

    private final List<EcosModule> modules = new CopyOnWriteArrayList<>();

    @Before
    public void before() {
        factory.getModuleTypesRegistry().getAll().forEach(def -> {
            @SuppressWarnings("unchecked")
            Class<EcosModule> type = (Class<EcosModule>) def.getType();
            eappsApi.getModuleApi().onModulePublished(type, this::onModulePublished);
        });
    }

    private void onModulePublished(EcosModule module) {
        synchronized (modules) {
            modules.add(module);
        }
    }

    @Test
    public void test() {

        EcosApp app = createApp();

        synchronized (modules) {

            ecosAppService.uploadApp("test", app, PublishPolicy.PUBLISH_IF_NOT_PUBLISHED);

            PublishStatus publishStatus = ecosAppService.getPublishStatus(app.getId());
            assertThat(publishStatus, is(PublishStatus.PUBLISHING));
        }

        TestUtils.waitUntil(() ->
            PublishStatus.PUBLISHING.equals(ecosAppService.getPublishStatus(app.getId())), 5);

        assertThat(modules.size(), is(app.getModules().size()));

        modules.forEach(m -> {

            String typeId = eappsModuleService.getTypeId(m.getClass());
            PublishStatus status = moduleService.getPublishStatus(ModuleRef.create(typeId, m.getId()));

            assertThat(status, is(PublishStatus.PUBLISHED));
        });

        Set<EcosModule> prev = new HashSet<>(app.getModules());
        Set<EcosModule> after = new HashSet<>(modules);

        assertThat(after, is(prev));

        synchronized (modules) {

            ecosAppService.uploadApp("test", app, PublishPolicy.PUBLISH_IF_NOT_PUBLISHED);

            PublishStatus publishStatus = ecosAppService.getPublishStatus(app.getId());
            assertThat(publishStatus, is(PublishStatus.PUBLISHED));
        }

        List<EcosModule> appModules = new ArrayList<>(app.getModules());
        FormModule form = new FormModule();
        form.setId("test-add-new-form");
        form.setFormKey("123");
        appModules.add(form);

        EcosApp newApp = new EcosAppImpl(createMeta(), appModules, app.getPatches());

        synchronized (modules) {

            ecosAppService.uploadApp("test", newApp, PublishPolicy.PUBLISH_IF_NOT_PUBLISHED);

            PublishStatus publishStatus = ecosAppService.getPublishStatus(newApp.getId());
            assertThat(publishStatus, is(PublishStatus.PUBLISHING));
        }

        TestUtils.waitUntil(() ->
            PublishStatus.PUBLISHING.equals(ecosAppService.getPublishStatus(newApp.getId())), 5);

        prev = new HashSet<>(newApp.getModules());
        after = new HashSet<>(modules);
        assertThat(after, is(prev));
    }

    private EcosApp createApp() {

        List<EcosModule> modules = new ArrayList<>();

        FormModule form = new FormModule();
        form.setId("form-0");
        form.setDefinition(JsonNodeFactory.instance.objectNode());
        form.setTitle("Form title 0");
        modules.add(form);

        form = new FormModule();
        form.setId("form-1");
        form.setDefinition(JsonNodeFactory.instance.objectNode());
        form.setTitle("Form title 1");
        modules.add(form);

        DashboardModule dashboardModule = new DashboardModule();
        dashboardModule.setId("dashboard-0");
        dashboardModule.setKey("test-key-0");
        modules.add(dashboardModule);

        dashboardModule = new DashboardModule();
        dashboardModule.setId("dashboard-1");
        dashboardModule.setKey("test-key-2");
        modules.add(dashboardModule);

        List<EcosPatch> patches = new ArrayList<>();

        return new EcosAppImpl(createMeta(), modules, patches);
    }

    private EcosAppMetaDto createMeta() {

        EcosAppMetaDto meta = new EcosAppMetaDto();
        meta.setId("test-app");
        meta.setVersion(new EcosAppVersion("1.0.0"));
        meta.setName("Test app name");

        Map<String, String> dependencies = new HashMap<>();
        dependencies.put("idocs-repo", "*");
        meta.setDependencies(dependencies);

        return meta;
    }
}
