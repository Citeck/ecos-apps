package ru.citeck.ecos.apps.app.module;

public interface EcosModule {

    String getId();

    String getType();

    String getName();

    int getModelVersion();

    String getMimetype();

    byte[] getData();
}
