package ru.citeck.ecos.apps.app.module;

import ru.citeck.ecos.apps.module.type.EcosModule;

public interface EcosModuleRev extends EcosModule {

    String getRevId();

    String getHash();

    long getSize();
}
