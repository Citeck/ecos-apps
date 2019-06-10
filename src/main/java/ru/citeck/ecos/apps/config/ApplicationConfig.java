package ru.citeck.ecos.apps.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;
import ru.citeck.ecos.apps.client.BasicAuthInterceptor;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.RecordsServiceFactory;
import ru.citeck.ecos.records2.source.dao.RecordsDAO;
import ru.citeck.ecos.records2.source.dao.remote.RecordsRestConnection;
import ru.citeck.ecos.records2.source.dao.remote.RemoteRecordsDAO;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class ApplicationConfig extends RecordsServiceFactory {

    @Bean
    RecordsService initRecordsService(List<RecordsDAO> recordsDAO) {

        RecordsService recordsService = createRecordsService();
        recordsDAO.forEach(recordsService::register);

        return recordsService;
    }

    @Bean
    RecordsDAO initAlfrescoRemoteRecordsDAO(@Qualifier("alfrescoRestTemplate")
                                                RestTemplate alfrescoRestTemplate) {

        RemoteRecordsDAO alfrescoRemote = new RemoteRecordsDAO();
        alfrescoRemote.setRecordsMethod("http://localhost:8080/alfresco/service/citeck/ecos/records/query");
        alfrescoRemote.setId("alfresco");
        alfrescoRemote.setRestConnection(new RecordsRestConnection() {
            @Override
            public <T> T jsonPost(String s, Object o, Class<T> aClass) {
                return alfrescoRestTemplate.postForObject(s, o, aClass);
            }
        });
        return alfrescoRemote;
    }

    @Bean({"alfrescoRestTemplate"})
    RestTemplate initAlfrescoRestTemplate(AlfrescoClientProperties properties) {

        RestTemplate alfrescoRestTemplate = new RestTemplate();

        ArrayList<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(alfrescoRestTemplate.getInterceptors());

        AlfrescoClientProperties.Authentication authentication = properties.getAuthentication();
        interceptors.add(new BasicAuthInterceptor(authentication.getUsername(), authentication.getPassword()));
        alfrescoRestTemplate.setInterceptors(interceptors);

        return alfrescoRestTemplate;
    }
}
