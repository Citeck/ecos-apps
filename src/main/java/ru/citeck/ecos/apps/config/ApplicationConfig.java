package ru.citeck.ecos.apps.config;

import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.records2.RecordsServiceFactory;

@Configuration
public class ApplicationConfig extends RecordsServiceFactory {

    /*@Bean
    RecordsService initRecordsService(List<RecordsDAO> recordsDAO) {

        RecordsService recordsService = createRecordsService();
        recordsDAO.forEach(recordsService::register);

        return recordsService;
    }*/
}
