package ru.citeck.ecos.apps.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.records2.spring.RecordsProperties;

@Configuration
public class ApplicationConfig {

    @Bean
    public RecordsProperties recordsProperties(ApplicationProperties appProps) {
        RecordsProperties props = appProps.getRecords();
        if (props == null) {
            props = new RecordsProperties();
        }
        return props;
    }
}
