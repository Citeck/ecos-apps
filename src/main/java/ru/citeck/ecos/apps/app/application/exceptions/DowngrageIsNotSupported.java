package ru.citeck.ecos.apps.app.application.exceptions;

import ru.citeck.ecos.apps.app.EcosApp;
import ru.citeck.ecos.apps.app.EcosAppVersion;

public class DowngrageIsNotSupported extends RuntimeException {

    public DowngrageIsNotSupported(EcosAppVersion before, EcosApp app) {
        super(
            "Downgrade is not supported. App: " + app.getId() + " " + app.getName()
                + " current: " + before.toString() + " new: " + app.getVersion()
        );
    }
}
