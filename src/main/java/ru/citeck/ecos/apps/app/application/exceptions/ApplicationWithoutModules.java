package ru.citeck.ecos.apps.app.application.exceptions;

import ru.citeck.ecos.apps.app.application.EcosAppReader;

public class ApplicationWithoutModules extends RuntimeException {

    public ApplicationWithoutModules(EcosAppReader.EcosAppDto dto) {
        super("Application without modules can't be uploaded. App: " + dto.getId() + " " + dto.getName());
    }
}
