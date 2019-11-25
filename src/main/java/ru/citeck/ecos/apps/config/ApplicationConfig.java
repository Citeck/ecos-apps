package ru.citeck.ecos.apps.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.records2.spring.RecordsProperties;

@Configuration
public class ApplicationConfig {

    private ApplicationProperties appProps;

    @Bean
    public RecordsProperties recordsProperties() {
        RecordsProperties props = appProps.getRecords();
        if (props == null) {
            props = new RecordsProperties();
        }
        return props;
    }

    @Autowired
    public void setAppProps(ApplicationProperties appProps) {
        this.appProps = appProps;
    }
}
