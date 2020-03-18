package ru.citeck.ecos.apps.app.application.exceptions;

import ru.citeck.ecos.apps.app.EcosApp;
import ru.citeck.ecos.commons.data.Version;

public class DowngrageIsNotSupported extends RuntimeException {

    public DowngrageIsNotSupported(Version before, EcosApp app) {
        super(
            "Downgrade is not supported. App: " + app.getId() + " " + app.getName()
                + " current: " + before.toString() + " new: " + app.getVersion()
        );
    }
}
