package ru.citeck.ecos.apps;

import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import ru.citeck.ecos.apps.app.application.props.ApplicationProperties;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import ru.citeck.ecos.webapp.lib.spring.EcosSpringApplication;
import ru.citeck.ecos.webapp.lib.spring.context.ecosconfig.EcosConfigBeanPostProcessor;

@SpringBootApplication
@EnableConfigurationProperties({
    ApplicationProperties.class
})
@EnableDiscoveryClient
@EnableJpaRepositories({
    "ru.citeck.ecos.apps.app.*.repo",
    "ru.citeck.ecos.apps.domain.*.repo",
    "ru.citeck.ecos.apps.domain.*.*.repo"
})
public class EcosAppsApp {

    public static final String NAME = "eapps";

    static {
        EcosConfigBeanPostProcessor.excludePackages("ru.citeck.ecos.apps.domain.ecosapp");
    }

    public static void main(String[] args) {
        new EcosSpringApplication(EcosAppsApp.class).run(args);
    }
}
