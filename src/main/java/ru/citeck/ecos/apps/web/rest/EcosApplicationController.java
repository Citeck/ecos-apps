package ru.citeck.ecos.apps.web.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import ru.citeck.ecos.apps.domain.EcosAppModuleEntity;
import ru.citeck.ecos.apps.service.EcosApplicationService;
import ru.citeck.ecos.records2.utils.MandatoryParam;

import javax.sql.rowset.serial.SerialBlob;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for EcosApplication
 */
@RestController
@RequestMapping("/application")
public class EcosApplicationController {

    private EcosApplicationService applicationService;

    @Autowired
    public EcosApplicationController(EcosApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    //todo: rename model to module
    @PostMapping("/model/deploy")
    public Map<String, String> deployModel(@RequestBody Model module) throws SQLException {

        MandatoryParam.check("type", module.type);
        MandatoryParam.check("data", module.data);
        MandatoryParam.check("key", module.key);
        MandatoryParam.check("name", module.name);
        MandatoryParam.check("mimetype", module.mimetype);

        EcosAppModuleEntity ecosAppModel = new EcosAppModuleEntity();

        ecosAppModel.setType(module.type);
        ecosAppModel.setKey(module.key);
        ecosAppModel.setData(new SerialBlob(module.data));

        ecosAppModel.setName(module.name);
        ecosAppModel.setMimetype(module.mimetype);

        applicationService.saveAndDeployModule(ecosAppModel);

        Map<String, String> result = new HashMap<>();
        result.put("status", "OK");

        return result;
    }

    private static class Model {

        public String key;
        public String name;
        public String type;
        public String mimetype;
        public byte[] data;
    }
}
