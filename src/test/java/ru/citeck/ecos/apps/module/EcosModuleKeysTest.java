package ru.citeck.ecos.apps.module;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import ru.citeck.ecos.apps.EcosAppsApp;
import ru.citeck.ecos.apps.app.module.EappsModuleService;
import ru.citeck.ecos.apps.app.module.EcosModuleRev;
import ru.citeck.ecos.apps.app.module.EcosModuleService;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.apps.app.module.type.ui.dashboard.DashboardModule;
import ru.citeck.ecos.metarepo.EcosMetaRepo;
import ru.citeck.ecos.records2.objdata.ObjectData;
import ru.citeck.ecos.records2.utils.json.JsonUtils;

import static org.junit.Assert.*;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = EcosAppsApp.class)
public class EcosModuleKeysTest {

    @Autowired
    private EcosModuleService moduleService;

    @Autowired
    private EappsModuleService eappsModuleService;

    @Test
    public void test() {

        ObjectData content0 = JsonUtils.read("{" +
                "\"test\":\"value\"" +
            "}", ObjectData.class);

        ObjectData content1 = JsonUtils.read("{" +
                "\"test2\":\"value2\"" +
            "}", ObjectData.class);

        String id0 = "id0";

        String typeId = eappsModuleService.getTypeId(DashboardModule.class);
        ModuleRef moduleRef0 = ModuleRef.create(typeId, id0);

        String key = "key-test";
        String type = "type-test";

        DashboardModule module0 = new DashboardModule();
        module0.setId(id0);
        module0.setKey(key);
        module0.setType(type);
        module0.setConfig(content0);

        String resId0 = moduleService.uploadModule("test", module0);
        assertEquals(id0, resId0);

        EcosModuleRev rev0 = moduleService.getLastModuleRev(moduleRef0);

        ObjectData data0 = JsonUtils.convert(eappsModuleService.read(rev0.getData(), typeId), ObjectData.class);
        assertEquals(content0, JsonUtils.convert(data0.get("config"), ObjectData.class));

        DashboardModule module1 = new DashboardModule();
        module1.setKey(key);
        module1.setType(type);
        module1.setConfig(content1);

        String resId1 = moduleService.uploadModule("test", module1);
        assertEquals(id0, resId1);

        EcosModuleRev rev10 = moduleService.getLastModuleRev(moduleRef0);

        ObjectData data1 = JsonUtils.convert(eappsModuleService.read(rev10.getData(), typeId), ObjectData.class);
        assertEquals(content1, JsonUtils.convert(data1.get("config"), ObjectData.class));
    }
}
