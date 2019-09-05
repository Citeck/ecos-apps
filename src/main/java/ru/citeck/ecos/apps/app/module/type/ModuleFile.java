package ru.citeck.ecos.apps.app.module.type;

import java.io.InputStream;
import java.util.function.Consumer;

public interface ModuleFile {

    void read(Consumer<InputStream> consumer);

    String getName();
}
