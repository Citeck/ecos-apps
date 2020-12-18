package ru.citeck.ecos.apps.domain.application.service;

import ru.citeck.ecos.apps.domain.module.service.EcosModuleRev;

import java.util.List;
import java.util.Map;

public interface EcosAppRev {

    String getId();

    String getName();

    //EcosAppVersion getVersion();

    Map<String, String> getDependencies();

    List<EcosModuleRev> getModules();

    //List<EcosPatch> getPatches();
}
