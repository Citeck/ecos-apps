package ru.citeck.ecos.apps.app.module.type.journal;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.IOUtils;
import org.springframework.util.MimeTypeUtils;
import ru.citeck.ecos.apps.app.module.EcosModule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.function.Consumer;

public class JournalModule /*implements EcosModule */{
/*
    public static final String TYPE = "journal";

    @Getter @Setter private String id;
    @Getter @Setter private String name;
    @Getter @Setter private String data;
    @Getter private final String type = TYPE;
    @Getter private final int modelVersion = 0;
    @Getter private final String mimetype = MimeTypeUtils.APPLICATION_XML_VALUE;

    @Override
    public void readData(StreamConsumer consumer) {
        try (InputStream in = IOUtils.toInputStream(data, Charset.forName("UTF-8"))) {
            consumer.accept(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }*/
}
