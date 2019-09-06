package ru.citeck.ecos.apps.app.module.type;

public interface ModuleFile {

    <T> T read(StreamConsumer<T> consumer);

    String getName();
}
