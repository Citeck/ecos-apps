package ru.citeck.ecos.apps.application;

import ru.citeck.ecos.apps.application.module.Dependency;
import ru.citeck.ecos.apps.application.module.EcosModule;

import java.util.List;

public interface EcosApp {

    String getId();

    String getName();

    String getVersion();

    List<Dependency> getDependencies();

    List<EcosModule> getModules();
}
