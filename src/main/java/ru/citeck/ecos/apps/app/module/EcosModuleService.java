package ru.citeck.ecos.apps.app.module;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EcosModuleService {

    private EcosModuleDao dao;

    public EcosModuleService(EcosModuleDao dao) {
        this.dao = dao;
    }

    public EcosModuleRev getLastModuleRev(String type, String id) {
        return new EcosModuleDb(dao.getLastModuleRev(type, id));
    }

    public EcosModuleRev getModuleRev(String id) {
        return new EcosModuleDb(dao.getModuleRev(id));
    }
}
