package ru.citeck.ecos.apps.application.module.type;

import java.io.InputStream;
import java.util.function.Consumer;

public interface ModuleFile {

    void read(Consumer<InputStream> consumer);

    String getName();
}
