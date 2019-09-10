package ru.citeck.ecos.apps.app.application;

import lombok.Data;
import ru.citeck.ecos.apps.app.module.EcosModuleDb;
import ru.citeck.ecos.apps.domain.EcosAppEntity;
import ru.citeck.ecos.apps.domain.EcosAppRevEntity;
import ru.citeck.ecos.apps.module.type.EcosModuleRev;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class EcosAppDb implements EcosAppRev {

    private String id;
    private String revId;
    private String name;

    private AppVersion version;
    private List<Dependency> dependencies;
    private List<EcosModuleRev> modules;
    private String hash;
    private long size;
    private String source;

    public EcosAppDb(EcosAppRevEntity entity) {

        EcosAppEntity application = entity.getApplication();
        id = application.getExtId();
        revId = entity.getExtId();
        name = entity.getName();
        version = new AppVersion(entity.getVersion());
        dependencies = new ArrayList<>();
        modules = entity.getModules()
            .stream()
            .map(EcosModuleDb::new)
            .collect(Collectors.toList());
        hash = entity.getHash();
        size = entity.getSize();
        source = entity.getSource();
    }
}
