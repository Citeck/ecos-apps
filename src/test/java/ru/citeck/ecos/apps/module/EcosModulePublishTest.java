package ru.citeck.ecos.apps.module;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import ru.citeck.ecos.apps.EcosAppsApiFactory;
import ru.citeck.ecos.apps.EcosAppsApp;
import ru.citeck.ecos.apps.TestUtils;
import ru.citeck.ecos.apps.app.PublishPolicy;
import ru.citeck.ecos.apps.app.PublishStatus;
import ru.citeck.ecos.apps.app.module.*;
import ru.citeck.ecos.apps.app.module.type.form.FormModule;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = EcosAppsApp.class)
public class EcosModulePublishTest {

    private static final String PUBLISH_ERR_MSG = "Test publish error";

    @Autowired
    private EcosModuleService moduleService;

    @Autowired
    private EappsModuleService eappsModuleService;

    @Autowired
    private EcosAppsApiFactory eappsApi;

    private final List<FormModule> forms = new CopyOnWriteArrayList<>();

    private boolean isInitialPublishing = true;

    @Before
    public void before() {

        eappsApi.getModuleApi().onModulePublished(FormModule.class, this::onModulePublished);
    }

    private void onModulePublished(FormModule module) {

        synchronized (forms) {

            if (isInitialPublishing) {
                isInitialPublishing = false;
                throw new RuntimeException(PUBLISH_ERR_MSG);
            }

            forms.add(module);
        }
    }

    @Test
    public void test() {

        String formTypeId = eappsModuleService.getTypeId(FormModule.class);
        String formId = "test";
        String source = "test";
        ModuleRef moduleRef = ModuleRef.create(formTypeId, formId);

        FormModule module = new FormModule();
        module.setId(formId);
        module.setFormMode("mode");
        module.setDefinition(JsonNodeFactory.instance.objectNode());

        synchronized (forms) {

            moduleService.uploadModule(source, module, PublishPolicy.PUBLISH_IF_NOT_PUBLISHED);

            TestUtils.waitWhile(() ->
                !PublishStatus.PUBLISHING.equals(moduleService.getPublishStatus(moduleRef)), 5);
        }

        TestUtils.waitWhile(() ->
            PublishStatus.PUBLISHING.equals(moduleService.getPublishStatus(moduleRef)), 5);

        ModulePublishState state = moduleService.getPublishState(moduleRef);
        assertThat(state.getStatus(), is(PublishStatus.PUBLISH_FAILED));
        assertThat(state.getMsg(), is(PUBLISH_ERR_MSG));
        assertThat(forms.size(), is(0));

        synchronized (forms) {

            moduleService.uploadModule(source, module, PublishPolicy.PUBLISH_IF_CHANGED);
            PublishStatus status = moduleService.getPublishStatus(moduleRef);

            assertThat(status, is(PublishStatus.PUBLISH_FAILED));
        }

        synchronized (forms) {

            moduleService.uploadModule(source, module, PublishPolicy.PUBLISH_IF_NOT_PUBLISHED);
            PublishStatus status = moduleService.getPublishStatus(moduleRef);

            assertThat(status, is(PublishStatus.PUBLISHING));
        }

        TestUtils.waitWhile(() ->
            PublishStatus.PUBLISHING.equals(moduleService.getPublishStatus(moduleRef)), 5);

        state = moduleService.getPublishState(moduleRef);
        assertThat(state.getStatus(), is(PublishStatus.PUBLISHED));
        assertThat(forms.size(), is(1));
        assertThat(forms.get(0), is(module));

        synchronized (forms) {

            moduleService.uploadModule(source, module, PublishPolicy.PUBLISH_IF_NOT_PUBLISHED);
            PublishStatus status = moduleService.getPublishStatus(moduleRef);

            assertThat(status, is(PublishStatus.PUBLISHED));
        }

        assertThat(forms.size(), is(1));
    }
}
