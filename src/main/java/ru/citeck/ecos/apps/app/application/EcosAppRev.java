package ru.citeck.ecos.apps.app.application;

import ru.citeck.ecos.apps.module.type.EcosModuleRev;

public interface EcosAppRev extends EcosAppBase<EcosModuleRev> {

    String getRevId();
}
