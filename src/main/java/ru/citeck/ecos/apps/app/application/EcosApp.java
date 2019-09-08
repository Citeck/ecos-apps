package ru.citeck.ecos.apps.app.application;

import ru.citeck.ecos.apps.module.type.EcosModule;

import java.util.List;

public interface EcosApp {

    String getId();

    String getName();

    AppVersion getVersion();

    List<Dependency> getDependencies();

    List<EcosModule> getModules();

    String getHash();

    long getSize();
}
