package ru.citeck.ecos.apps.app.application.exceptions;

import ru.citeck.ecos.apps.app.application.AppVersion;
import ru.citeck.ecos.apps.app.application.EcosApp;

public class DowngrageIsNotSupported extends RuntimeException {

    public DowngrageIsNotSupported(AppVersion before, EcosApp app) {
        super(
            "Downgrade is not supported. App: " + app.getId() + " " + app.getName()
                + " current: " + before.toString() + " new: " + app.getVersion()
        );
    }
}
