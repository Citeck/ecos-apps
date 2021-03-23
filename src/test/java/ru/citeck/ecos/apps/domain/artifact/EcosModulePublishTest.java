package ru.citeck.ecos.apps.domain.artifact;

import lombok.extern.slf4j.Slf4j;

import static org.junit.Assert.assertThat;

@Slf4j
//@RunWith(SpringRunner.class)
//@SpringBootTest(classes = EcosAppsApp.class)
public class EcosModulePublishTest {

    /*private static final String PUBLISH_ERR_MSG = "Test publish error";

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
        ArtifactRef moduleRef = ArtifactRef.create(formTypeId, formId);

        FormModule module = new FormModule();
        module.setId(formId);
        module.setFormMode("mode");
        module.setDefinition(new ObjectData());

        synchronized (forms) {

            moduleService.uploadModule(source, module, PublishPolicy.PUBLISH_IF_NOT_PUBLISHED);

            TestUtils.waitWhile(() ->
                !PublishStatus.PUBLISHING.equals(moduleService.getDeployStatus(moduleRef)), 5);
        }

        TestUtils.waitWhile(() ->
            PublishStatus.PUBLISHING.equals(moduleService.getDeployStatus(moduleRef)), 5);

        ModulePublishState state = moduleService.getDeployState(moduleRef);
        assertThat(state.getStatus(), is(PublishStatus.PUBLISH_FAILED));
        assertThat(state.getMsg(), is(PUBLISH_ERR_MSG));
        assertThat(forms.size(), is(0));

        synchronized (forms) {

            moduleService.uploadModule(source, module, PublishPolicy.PUBLISH_IF_CHANGED);
            PublishStatus status = moduleService.getDeployStatus(moduleRef);

            assertThat(status, is(PublishStatus.PUBLISH_FAILED));
        }

        synchronized (forms) {

            moduleService.uploadModule(source, module, PublishPolicy.PUBLISH_IF_NOT_PUBLISHED);
            PublishStatus status = moduleService.getDeployStatus(moduleRef);

            assertThat(status, is(PublishStatus.PUBLISHING));
        }

        TestUtils.waitWhile(() ->
            PublishStatus.PUBLISHING.equals(moduleService.getDeployStatus(moduleRef)), 5);

        state = moduleService.getDeployState(moduleRef);
        assertThat(state.getStatus(), is(PublishStatus.DEPLOYED));
        assertThat(forms.size(), is(1));
        assertThat(forms.get(0), is(module));

        synchronized (forms) {

            moduleService.uploadModule(source, module, PublishPolicy.PUBLISH_IF_NOT_PUBLISHED);
            PublishStatus status = moduleService.getDeployStatus(moduleRef);

            assertThat(status, is(PublishStatus.DEPLOYED));
        }

        assertThat(forms.size(), is(1));
    }*/
}
