package ru.citeck.ecos.apps.app.module;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.apps.EcosAppsApiFactory;
import ru.citeck.ecos.apps.app.module.api.ModulePublishMsg;

@Slf4j
@Service
public class ModulePublishService {

    private EcosAppsApiFactory appsApi;

    public ModulePublishService(EcosAppsApiFactory appsApi) {
        this.appsApi = appsApi;
    }

    public void publish(EcosModuleRev module) {

        ModulePublishMsg msg = new ModulePublishMsg();

        msg.setId(module.getId());
        msg.setRevId(module.getRevId());
        msg.setName(module.getName());
        msg.setType(module.getType());
        msg.setDataType(module.getDataType());
        msg.setModelVersion(module.getModelVersion());

        msg.setHash(module.getHash());
        msg.setSize(module.getSize());
        msg.setData(module.getData());

        log.debug("Convert and send module: " + msg);
        appsApi.getModuleApi().publishModule(msg);
    }
}
