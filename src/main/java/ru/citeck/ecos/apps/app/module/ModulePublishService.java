package ru.citeck.ecos.apps.app.module;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.apps.queue.ModulePublishMsg;
import ru.citeck.ecos.apps.queue.EcosAppQueues;

@Slf4j
@Service
public class ModulePublishService {

    private AmqpTemplate amqpTemplate;

    public ModulePublishService(@Autowired(required = false) AmqpTemplate amqpTemplate) {
        this.amqpTemplate = amqpTemplate;
    }

    public void publish(EcosModuleRev module) {

        if (amqpTemplate == null) {
            throw new IllegalStateException("AmqpTemplate is not initialized!");
        }

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
        amqpTemplate.convertAndSend(EcosAppQueues.MODULES_EXCHANGE_ID, module.getType(), msg);
    }
}
