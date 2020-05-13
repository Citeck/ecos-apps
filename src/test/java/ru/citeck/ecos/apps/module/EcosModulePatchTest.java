package ru.citeck.ecos.apps.module;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import ru.citeck.ecos.apps.EcosAppsApp;
import ru.citeck.ecos.commands.CommandsServiceFactory;
import ru.citeck.ecos.commons.data.ObjectData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = EcosAppsApp.class)
public class EcosModulePatchTest {

    @Autowired
    private TestModuleHandler testModules;

    private CommandsServiceFactory commandsServiceFactory;

    @Test
    public void test() throws InterruptedException {

        Thread.sleep(5000);

        ObjectData module = testModules.getById("test-module");
        assertNotNull(module);

        assertEquals("changed-value", module.get("/config/key0").asText());
    }
}
