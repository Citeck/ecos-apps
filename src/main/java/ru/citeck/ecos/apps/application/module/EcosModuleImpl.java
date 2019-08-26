package ru.citeck.ecos.apps.application.module;

import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.function.Consumer;

public class EcosModuleImpl implements EcosModuleRev {

    @Getter @Setter private String id;
    @Getter @Setter private String type;
    @Getter @Setter private String key;
    @Getter @Setter private String name;
    @Getter @Setter private int modelVersion;
    @Getter @Setter private String mimetype;
    @Getter @Setter private String revId;

    @Setter private Blob data;

    @Override
    public void readData(Consumer<InputStream> consumer) {
        try (InputStream in = data.getBinaryStream()) {
            consumer.accept(in);
        } catch (IOException | SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
