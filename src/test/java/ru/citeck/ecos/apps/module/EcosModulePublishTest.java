package ru.citeck.ecos.apps.module;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
import org.hibernate.Cache;
import org.hibernate.SessionFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import ru.citeck.ecos.apps.EcosAppsApiFactory;
import ru.citeck.ecos.apps.EcosAppsApp;
import ru.citeck.ecos.apps.TestUtils;
import ru.citeck.ecos.apps.app.PublishStatus;
import ru.citeck.ecos.apps.app.module.*;
import ru.citeck.ecos.apps.app.module.type.form.FormModule;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

    @Autowired
    private DataSource dataSource;

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

            moduleService.uploadModule(source, module);

            TestUtils.waitUntil(() -> {

                try (Connection connection = dataSource.getConnection();
                     Statement statement = connection.createStatement()) {

                    ResultSet resultSet = statement.executeQuery("SELECT * FROM ecos_module WHERE ext_id = 'test'");
                    resultSet.next();
                    int status = resultSet.getInt("publish_status");

                    switch (status) {
                        case 0:
                            log.info("STATUS FROM DB: DRAFT");
                            break;
                        case 1:
                            log.info("STATUS FROM DB: PUBLISHING");
                            break;
                        case 2:
                            log.info("STATUS FROM DB: PUBLISHED");
                            break;
                        case 3:
                            log.info("STATUS FROM DB: PUBLISH_FAILED");
                            break;
                    }


                } catch (SQLException e) {
                    log.error("error", e);
                }

                log.info("STATUS CHECK: " + moduleService.getPublishStatus(moduleRef));
                return !PublishStatus.PUBLISHING.equals(moduleService.getPublishStatus(moduleRef));
            }, 5);
        }

        TestUtils.waitUntil(() ->
            PublishStatus.PUBLISHING.equals(moduleService.getPublishStatus(moduleRef)), 5);

        ModulePublishState state = moduleService.getPublishState(moduleRef);
        assertThat(state.getStatus(), is(PublishStatus.PUBLISH_FAILED));
        assertThat(state.getMsg(), is(PUBLISH_ERR_MSG));
        assertThat(forms.size(), is(0));

        synchronized (forms) {

            moduleService.uploadModule(source, module);
            PublishStatus status = moduleService.getPublishStatus(moduleRef);

            assertThat(status, is(PublishStatus.PUBLISHING));
        }

        TestUtils.waitUntil(() ->
            PublishStatus.PUBLISHING.equals(moduleService.getPublishStatus(moduleRef)), 5);

        state = moduleService.getPublishState(moduleRef);
        assertThat(state.getStatus(), is(PublishStatus.PUBLISHED));
        assertThat(forms.size(), is(1));
        assertThat(forms.get(0), is(module));
    }
}
