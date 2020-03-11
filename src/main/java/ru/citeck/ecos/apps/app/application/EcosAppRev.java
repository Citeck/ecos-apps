package ru.citeck.ecos.apps.app.application;

import ru.citeck.ecos.apps.app.module.EcosModuleRev;

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
