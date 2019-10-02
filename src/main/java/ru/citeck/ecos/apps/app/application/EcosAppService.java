package ru.citeck.ecos.apps.app.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class EcosAppService {

    private EcosAppDao appDao;

    public EcosAppService(EcosAppDao appDao) {
        this.appDao = appDao;
    }

    public EcosAppRev uploadApp(String source, byte[] data) {
        return new EcosAppDb(appDao.uploadApp(source, data));
    }
}
