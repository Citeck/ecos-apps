package ru.citeck.ecos.apps.app.application;

import ru.citeck.ecos.apps.module.type.EcosModule;

import java.util.List;

public interface EcosAppBase<T extends EcosModule> {

    String getId();

    String getName();

    AppVersion getVersion();

    List<Dependency> getDependencies();

    List<T> getModules();

    String getHash();

    long getSize();

    String getSource();
}
