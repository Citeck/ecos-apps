package ru.citeck.ecos.apps.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.EcosAppMetaDto;
import ru.citeck.ecos.apps.app.EcosAppVersion;
import ru.citeck.ecos.apps.app.provider.DirectoryAppProvider;
import ru.citeck.ecos.apps.app.provider.EcosAppsProvider;
import ru.citeck.ecos.apps.spring.EcosAppsDeployer;
import ru.citeck.ecos.records2.RecordsProperties;

import java.io.IOException;
import java.util.Collections;

@Configuration
public class ApplicationConfig {

    private ApplicationProperties appProps;

    @Bean
    @ConfigurationProperties(prefix = "ecos-apps.ecos-records")
    public RecordsProperties recordsProperties() {
        return new RecordsProperties();
    }

    @Bean
    public EcosAppsProvider appsProvider(ResourceLoader loader) throws IOException {

        ApplicationProperties.EappConfig app = appProps.getEcosApp();

        if (app == null) {
            return io -> Collections.emptyList();
        }

        EcosAppMetaDto meta = new EcosAppMetaDto();
        meta.setId(app.getId());
        meta.setName(app.getName());

        String version = app.getVersion().replaceAll("[^\\d.]+", "");
        meta.setVersion(new EcosAppVersion(version));

        Resource appsDir = loader.getResource("classpath:" + app.getFolder());

        return new DirectoryAppProvider(appsDir.getFile(), meta);
    }

    @Autowired
    public void setAppProps(ApplicationProperties appProps) {
        this.appProps = appProps;
    }

    @Component
    public static class AppDeployer {

        private EcosAppsDeployer appsDeployer;

        @EventListener
        public void onApplicationEvent(ContextRefreshedEvent event) {
            appsDeployer.deploy();
        }

        @Autowired
        public void setAppsDeployer(EcosAppsDeployer appsDeployer) {
            this.appsDeployer = appsDeployer;
        }
    }
}
