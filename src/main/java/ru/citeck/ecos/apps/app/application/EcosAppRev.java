package ru.citeck.ecos.apps.app.application;

import ru.citeck.ecos.apps.app.EcosAppBase;
import ru.citeck.ecos.apps.app.module.EcosModuleRev;

public interface EcosAppRev extends EcosAppBase<EcosModuleRev> {

    String getRevId();
}
