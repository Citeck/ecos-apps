package ru.citeck.ecos.apps.web.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import ru.citeck.ecos.apps.app.module.EcosModuleService;
import ru.citeck.ecos.apps.module.type.DataType;
import ru.citeck.ecos.apps.module.type.EcosModuleRev;
import ru.citeck.ecos.apps.queue.DeployModuleMsg;
import ru.citeck.ecos.apps.queue.EcosAppQueues;

/**
 * Controller for EcosApplication
 */
@Slf4j
@RestController
@RequestMapping("/app")
public class EcosAppController {

    @Autowired
    AmqpTemplate template;

    private EcosModuleService moduleService;

    public EcosAppController(EcosModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @GetMapping("/module/rev/{moduleRevId}")
    public HttpEntity<byte[]> downloadModule(@PathVariable String moduleRevId) {
        return toDownloadHttpEntity(moduleService.getModuleRev(moduleRevId));
    }

    @GetMapping("/module/type/{type}/{moduleId}")
    public HttpEntity<byte[]> downloadModule(@PathVariable String type,
                                             @PathVariable String moduleId) {
        return toDownloadHttpEntity(moduleService.getLastModuleRev(type, moduleId));
    }

    private HttpEntity<byte[]> toDownloadHttpEntity(EcosModuleRev rev) {

        String filename = rev.getId() + "." + rev.getDataType().getExt();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
            ContentDisposition.builder("attachment")
                .filename(filename)
                .build());

        return new HttpEntity<>(rev.getData(), headers);
    }

    @GetMapping("/rabbit")
    public String test(String msg) {

        DeployModuleMsg msgg = new DeployModuleMsg();
        msgg.setId("test_id");
        msgg.setDataType(DataType.JSON);
        msgg.setDataUrl("ecosapps/...");
        msgg.setHash("123123");
        msgg.setModelVersion(0);
        msgg.setSize(123123);

        //template.convertAndSend()
        template.convertAndSend(EcosAppQueues.QUEUE_DEPLOY_ID, msgg);
        return "OK";
    }

    @RabbitListener(queues = EcosAppQueues.QUEUE_DEPLOY_ID)
    public void worker1(DeployModuleMsg message) {
        //logger.info("worker 1 : " + message);
        //Thread.sleep(100 * random.nextInt(20));
        log.info("Receive rabbit message: " + message);
    }

    /*
    *"ECOS-NOTIFICATIONS_EVENT_HOST": "rabbitmq",
    "ECOS-NOTIFICATIONS_EVENT_PORT": "5672",
    "ECOS-NOTIFICATIONS_EVENT_USERNAME": "rabbitmqadmin",
    "ECOS-NOTIFICATIONS_EVENT_PASSWORD": "",
    *
    * */

    //todo: rename model to module
    /*@PostMapping("/model/deploy")
    public Map<String, String> deployModel(@RequestBody Model module) throws SQLException {

        MandatoryParam.check("type", module.type);
        MandatoryParam.check("data", module.data);
        MandatoryParam.check("key", module.key);
        MandatoryParam.check("name", module.name);
        MandatoryParam.check("mimetype", module.mimetype);

        EcosModuleEntity ecosAppModel = new EcosModuleEntity();

        ecosAppModel.setType(module.type);
        //ecosAppModel.setKey(module.key);
        //ecosAppModel.setData(new SerialBlob(module.data));

        //ecosAppModel.setName(module.name);
        //ecosAppModel.setMimetype(module.mimetype);

        //applicationService.saveAndDeployModule(ecosAppModel);

        Map<String, String> result = new HashMap<>();
        result.put("status", "OK");

        return result;
    }*/

   /* private static class Model {

        public String key;
        public String name;
        public String type;
        public String mimetype;
        public byte[] data;
    }*/
}
