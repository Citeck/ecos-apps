package ru.citeck.ecos.apps.app.application.exceptions;

public class ApplicationWithoutModules extends RuntimeException {

    public ApplicationWithoutModules(String id, String name) {
        super("Application without modules can't be uploaded. App: " + id + " " + name);
    }
}
