package ru.citeck.ecos.apps.application.module.type.journal;

import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import ru.citeck.ecos.apps.application.module.EcosModule;

import java.io.InputStream;
import java.util.function.Consumer;

public class JournalModule implements EcosModule {

    public static final String TYPE = "journal";

    private String id;
    private String key;
    private String name;
    private String data;

    @Override
    public String getId() {
        return null;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getKey() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public int getModelVersion() {
        return 0;
    }

    @Override
    public String getMimetype() {
        return MimeTypeUtils.APPLICATION_XML_VALUE;
    }

    @Override
    public void readData(Consumer<InputStream> consumer) {

    }
}
