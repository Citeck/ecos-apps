package ru.citeck.ecos.apps.app.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Service
@Transactional
public class EcosApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(EcosApplicationService.class);

    private EcosAppReader reader;

    @PostConstruct
    void init() throws IOException {
        ClassPathResource resource = new ClassPathResource("test/test-app.zip");
        upload(resource.getURI().toString());
    }

    public EcosAppRev upload(String location) {
        return upload(location, false);
    }

    public EcosAppRev upload(String location, boolean deploy) {

        return null;
        ///return reader.load(location);
    }

    public EcosAppRev upload(Resource resource, boolean deploy) {
       // reader.load(resource)
        return null;
    }
}
