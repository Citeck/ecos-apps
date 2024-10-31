package ru.citeck.ecos.apps.app.application.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Properties specific to ecos-apps.
 * <p>
 * Properties are configured in the application.yml file.
 */
@Data
@ConfigurationProperties(prefix = "ecos-apps")
public class ApplicationProperties {

    private EappConfig ecosApp;

    @Data
    public static class EappConfig {
        private String id;
        private String name;
        private String folder;
        private String version;
        private Map<String, String> dependencies;
    }
}
