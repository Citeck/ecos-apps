package ru.citeck.ecos.apps.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.records2.RecordsProperties;

@Configuration
public class ApplicationConfig {

    private ApplicationProperties appProps;

    @Bean
    @ConfigurationProperties(prefix = "ecos-apps.ecos-records")
    public RecordsProperties recordsProperties() {
        return new RecordsProperties();
    }

    @Autowired
    public void setAppProps(ApplicationProperties appProps) {
        this.appProps = appProps;
    }
}
