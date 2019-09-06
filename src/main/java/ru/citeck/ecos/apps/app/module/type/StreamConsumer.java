package ru.citeck.ecos.apps.app.module.type;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
public interface StreamConsumer<T> {

    T accept(InputStream in) throws IOException;
}
