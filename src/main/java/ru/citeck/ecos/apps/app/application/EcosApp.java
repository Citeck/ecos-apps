package ru.citeck.ecos.apps.app.application;

import ru.citeck.ecos.apps.app.module.Dependency;
import ru.citeck.ecos.apps.app.module.EcosModule;

import java.util.List;

public interface EcosApp {

    String getId();

    String getName();

    String getVersion();

    List<Dependency> getDependencies();

    List<EcosModule> getModules();

    String getHash();

    long getSize();

    void dispose();
}
