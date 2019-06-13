package ru.citeck.ecos.apps.service;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import ru.citeck.ecos.apps.config.ApplicationProperties;
import ru.citeck.ecos.apps.domain.EcosAppModule;
import ru.citeck.ecos.apps.domain.EcosApplication;
import ru.citeck.ecos.apps.repository.EcosAppModuleRepository;
import ru.citeck.ecos.apps.repository.EcosApplicationRepository;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

@Service
@Transactional
public class EcosApplicationService {

    private ApplicationProperties properties;
    private EcosAppModuleRepository moduleRepository;
    private EcosApplicationRepository applicationRepository;

    private RestTemplate alfrescoRestTemplate;

    @Autowired
    public EcosApplicationService(EcosApplicationRepository applicationRepository,
                                  EcosAppModuleRepository moduleRepository,
                                  ApplicationProperties properties,
                                  @Qualifier("alfrescoRestTemplate") RestTemplate alfrescoRestTemplate) {

        this.properties = properties;
        this.moduleRepository = moduleRepository;
        this.alfrescoRestTemplate = alfrescoRestTemplate;
        this.applicationRepository = applicationRepository;
    }

    public void saveAndDeployModule(EcosAppModule module) {

        if (module.getKey() == null
            || module.getData() == null
            || module.getMimetype() == null
            || module.getType() == null) {

            throw new IllegalArgumentException("Key, data, type and format is a " +
                                               "mandatory parameters. Model: " + module);
        }

        EcosAppModule existing = moduleRepository.getLastByKey(module.getType(), module.getKey());

        if (existing != null) {
            module.setVersion(existing.getVersion() + 1);
        }

        module = moduleRepository.save(module);

        EcosApplication app;

        String appKey = module.getKey() + "-application";

        app = new EcosApplication();
        app.setKey(appKey);
        app.setName(appKey);
        app.setModules(Collections.singleton(module));

        EcosApplication existingApp = applicationRepository.getLastByKey(appKey);
        if (existingApp != null) {
            app.setVersion(existingApp.getVersion() + 1);
        }

        app = applicationRepository.save(app);
        deployApplication(app);
    }

    public void deployApplication(EcosApplication application) {

        Map<String, String> deployUrl = properties.getDeployUrl();
        Map<String, EcosAppModule> modules = new HashMap<>();

        for (EcosAppModule module : application.getModules()) {

            String url = deployUrl.get(module.getType());
            if (StringUtils.isBlank(url)) {
                throw new IllegalStateException("Module type is unknown: " + module.getType() + " module: " + module);
            }

            modules.put(url, module);
        }

        modules.forEach((url, module) ->
            alfrescoRestTemplate.postForObject(url, new ModuleToDeploy(module), Object.class));
    }


    private static class ModuleToDeploy {

        private final EcosAppModule module;

        ModuleToDeploy(EcosAppModule module) {
            this.module = module;
        }

        public long getVersion() {
            return module.getVersion();
        }

        public String getKey() {
            return module.getKey();
        }

        public String getName() {
            return module.getName();
        }

        public String getType() {
            return module.getType();
        }

        public String getMimetype() {
            return module.getMimetype();
        }

        public byte[] getData() throws SQLException, IOException {
            return IOUtils.toByteArray(module.getData().getBinaryStream());
        }
    }
}