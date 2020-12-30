package ru.citeck.ecos.apps.domain.application.service;

import ru.citeck.ecos.apps.domain.artifact.service.EcosArtifactRev;

import java.util.List;
import java.util.Map;

public interface EcosAppRev {

    String getId();

    String getName();

    //EcosAppVersion getVersion();

    Map<String, String> getDependencies();

    List<EcosArtifactRev> getModules();

    //List<EcosPatch> getPatches();
}
