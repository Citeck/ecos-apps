package ru.citeck.ecos.apps.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.citeck.ecos.records2.spring.RecordsProperties;

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
}
