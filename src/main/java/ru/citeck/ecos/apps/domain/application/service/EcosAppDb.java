package ru.citeck.ecos.apps.domain.application.service;

import lombok.Data;
import ru.citeck.ecos.apps.domain.artifact.service.EcosArtifactDb;
import ru.citeck.ecos.apps.domain.artifact.service.EcosArtifactRev;
import ru.citeck.ecos.apps.domain.application.repo.EcosAppEntity;
import ru.citeck.ecos.apps.domain.application.repo.EcosAppRevEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class EcosAppDb /*implements EcosAppRev*/ {

    private String id;
    private String revId;
    private String name;

    //private EcosAppVersion version;
    private Map<String, String> dependencies;
    private List<EcosArtifactRev> modules;
    //private List<EcosPatch> patches;
    private String hash;
    private long size;
    private String source;

    public EcosAppDb(EcosAppRevEntity entity) {

        EcosAppEntity application = entity.getApplication();
        id = application.getExtId();
        revId = entity.getExtId();
        name = entity.getName();
        //version = new EcosAppVersion(entity.getVersion());
        dependencies = new HashMap<>();
        modules = entity.getModules()
            .stream()
            .map(EcosArtifactDb::new)
            .collect(Collectors.toList());
    }
}
