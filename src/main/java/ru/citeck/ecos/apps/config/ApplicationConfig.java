package ru.citeck.ecos.apps.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.citeck.ecos.apps.spring.EcosAppsFactoryConfig;
import ru.citeck.ecos.records2.RecordsServiceFactory;

@Configuration
@Import(EcosAppsFactoryConfig.class)
public class ApplicationConfig extends RecordsServiceFactory {

    /*@Bean
    RecordsService initRecordsService(List<RecordsDAO> recordsDAO) {

        RecordsService recordsService = createRecordsService();
        recordsDAO.forEach(recordsService::register);

        return recordsService;
    }*/
}
