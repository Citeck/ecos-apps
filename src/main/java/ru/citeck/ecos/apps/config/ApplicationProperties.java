package ru.citeck.ecos.apps.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Properties specific to Ecosapps.
 * <p>
 * Properties are configured in the application.yml file.
 * See {@link io.github.jhipster.config.JHipsterProperties} for a good example.
 */
@ConfigurationProperties(prefix = "ecosapps.application", ignoreUnknownFields = false)
public class ApplicationProperties {

    private Map<String, String> deployUrl;

    public Map<String, String> getDeployUrl() {
        return deployUrl;
    }

    public void setDeployUrl(Map<String, String> deployUrl) {
        this.deployUrl = deployUrl;
    }
}
