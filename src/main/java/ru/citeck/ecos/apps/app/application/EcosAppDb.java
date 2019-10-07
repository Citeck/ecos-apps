package ru.citeck.ecos.apps.app.application;

import lombok.Data;
import ru.citeck.ecos.apps.app.EcosAppVersion;
import ru.citeck.ecos.apps.app.module.EcosModuleDb;
import ru.citeck.ecos.apps.app.module.EcosModuleRev;
import ru.citeck.ecos.apps.domain.EcosAppEntity;
import ru.citeck.ecos.apps.domain.EcosAppRevEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class EcosAppDb implements EcosAppRev {

    private String id;
    private String revId;
    private String name;

    private EcosAppVersion version;
    private Map<String, String> dependencies;
    private List<EcosModuleRev> modules;
    private String hash;
    private long size;
    private String source;

    public EcosAppDb(EcosAppRevEntity entity) {

        EcosAppEntity application = entity.getApplication();
        id = application.getExtId();
        revId = entity.getExtId();
        name = entity.getName();
        version = new EcosAppVersion(entity.getVersion());
        dependencies = new HashMap<>();
        modules = entity.getModules()
            .stream()
            .map(EcosModuleDb::new)
            .collect(Collectors.toList());
    }
}
