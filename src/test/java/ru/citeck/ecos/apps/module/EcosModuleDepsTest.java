package ru.citeck.ecos.apps.module;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import ru.citeck.ecos.apps.EcosAppsApiFactory;
import ru.citeck.ecos.apps.EcosAppsApp;
import ru.citeck.ecos.apps.TestUtils;
import ru.citeck.ecos.apps.app.PublishStatus;
import ru.citeck.ecos.apps.app.module.EappsModuleService;
import ru.citeck.ecos.apps.app.module.EcosModuleService;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.apps.app.module.type.model.type.TypeModule;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = EcosAppsApp.class)
public class EcosModuleDepsTest {

    @Autowired
    private EcosModuleService moduleService;

    @Autowired
    private EappsModuleService eappsModuleService;

    @Autowired
    private EcosAppsApiFactory eappsApi;

    private final List<TypeModule> publishedTypes = new CopyOnWriteArrayList<>();

    @Test
    public void test() {

        eappsApi.getModuleApi().onModulePublished(TypeModule.class, this::onModulePublished);

        TypeModule baseTypeModule = new TypeModule();
        baseTypeModule.setId("base");
        baseTypeModule.setDescription("Base type");

        TypeModule otherTypeModule = new TypeModule();
        otherTypeModule.setId("custom-type");

        String typeId = eappsModuleService.getTypeId(TypeModule.class);

        ModuleRef baseModuleRef = ModuleRef.create(typeId, baseTypeModule.getId());
        otherTypeModule.setParent(baseModuleRef);

        ModuleRef otherModuleRef = ModuleRef.create(typeId, otherTypeModule.getId());

        synchronized (publishedTypes) {

            moduleService.uploadModule("test", otherTypeModule);

            assertThat(moduleService.getPublishStatus(otherModuleRef), is(PublishStatus.DEPS_WAITING));
        }

        assertThat(publishedTypes.size(), is(0));

        synchronized (publishedTypes) {

            moduleService.uploadModule("test", baseTypeModule);

            assertThat(moduleService.getPublishStatus(baseModuleRef), is(PublishStatus.PUBLISHING));
            assertThat(moduleService.getPublishStatus(otherModuleRef), is(PublishStatus.DEPS_WAITING));
        }

        TestUtils.waitWhile(() -> moduleService.getPublishStatus(baseModuleRef).equals(PublishStatus.PUBLISHING), 5);

        assertThat(moduleService.getPublishStatus(baseModuleRef), is(PublishStatus.PUBLISHED));

        TestUtils.waitWhile(() -> !moduleService.getPublishStatus(otherModuleRef).equals(PublishStatus.PUBLISHED), 5);

        assertThat(publishedTypes.size(), is(2));
        assertThat(publishedTypes.get(0), is(baseTypeModule));
        assertThat(publishedTypes.get(1), is(otherTypeModule));
    }

    private void onModulePublished(TypeModule type) {
        synchronized (publishedTypes) {
            publishedTypes.add(type);
        }
    }
}
