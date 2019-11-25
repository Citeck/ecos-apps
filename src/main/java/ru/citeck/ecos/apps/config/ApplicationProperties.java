package ru.citeck.ecos.apps.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.citeck.ecos.records2.spring.RecordsProperties;

import java.util.Map;

/**
 * Properties specific to ecos-apps.
 * <p>
 * Properties are configured in the application.yml file.
 * See {@link io.github.jhipster.config.JHipsterProperties} for a good example.
 */
@Data
@ConfigurationProperties(prefix = "ecos-apps")
public class ApplicationProperties {

    private RecordsProperties records;
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
