package ru.citeck.ecos.apps.app.module;

import lombok.Getter;

/**
 * Warning! Ordinal used in DB. Do not change values order!
 */
public enum DataType {

    JSON("json"),
    XML("xml"),
    YAML("yaml"),
    ZIP("zip"),
    JAR("jar");

    @Getter private final String ext;

    DataType(String ext) {
        this.ext = ext;
    }
}
