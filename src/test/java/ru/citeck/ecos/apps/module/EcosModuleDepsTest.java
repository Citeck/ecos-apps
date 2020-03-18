package ru.citeck.ecos.apps.module;

import lombok.extern.slf4j.Slf4j;

import static org.junit.Assert.assertThat;

@Slf4j
//@RunWith(SpringRunner.class)
//@SpringBootTest(classes = EcosAppsApp.class)
public class EcosModuleDepsTest {

   /* @Autowired
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
        baseTypeModule.setDescription(new MLText("Base type"));

        TypeModule otherTypeModule = new TypeModule();
        otherTypeModule.setId("custom-type");

        String typeId = eappsModuleService.getTypeId(TypeModule.class);

        ModuleRef baseModuleRef = ModuleRef.create(typeId, baseTypeModule.getId());
        otherTypeModule.setParent(baseModuleRef);

        ModuleRef otherModuleRef = ModuleRef.create(typeId, otherTypeModule.getId());

        synchronized (publishedTypes) {

            moduleService.uploadModule("test", otherTypeModule);

            assertThat(moduleService.getDeployStatus(otherModuleRef), is(PublishStatus.DEPS_WAITING));
        }

        assertThat(publishedTypes.size(), is(0));

        synchronized (publishedTypes) {

            moduleService.uploadModule("test", baseTypeModule);

            assertThat(moduleService.getDeployStatus(baseModuleRef), is(PublishStatus.PUBLISHING));
            assertThat(moduleService.getDeployStatus(otherModuleRef), is(PublishStatus.DEPS_WAITING));
        }

        TestUtils.waitWhile(() -> moduleService.getDeployStatus(baseModuleRef).equals(PublishStatus.PUBLISHING), 5);

        assertThat(moduleService.getDeployStatus(baseModuleRef), is(PublishStatus.DEPLOYED));

        TestUtils.waitWhile(() -> !moduleService.getDeployStatus(otherModuleRef).equals(PublishStatus.DEPLOYED), 5);

        assertThat(publishedTypes.size(), is(2));
        assertThat(publishedTypes.get(0), is(baseTypeModule));
        assertThat(publishedTypes.get(1), is(otherTypeModule));
    }

    private void onModulePublished(TypeModule type) {
        synchronized (publishedTypes) {
            publishedTypes.add(type);
        }
    }*/
}
