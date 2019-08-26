package ru.citeck.ecos.apps.application.module;

import java.io.InputStream;
import java.util.function.Consumer;

public interface EcosModule {

    String getId();

    String getType();

    String getKey();

    String getName();

    int getModelVersion();

    String getMimetype();

    void readData(Consumer<InputStream> consumer);
}
