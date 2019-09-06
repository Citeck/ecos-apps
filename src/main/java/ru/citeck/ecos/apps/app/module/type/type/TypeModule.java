package ru.citeck.ecos.apps.app.module.type.type;

import lombok.Getter;
import lombok.Setter;
import ru.citeck.ecos.apps.app.module.DataType;
import ru.citeck.ecos.apps.app.module.EcosModule;

import java.nio.charset.StandardCharsets;

public class TypeModule implements EcosModule {

    public static final String TYPE = "type";

    private String data;

    @Getter @Setter private String id;
    @Getter @Setter private String name;

    public TypeModule(String data, String id, String name) {
        this.data = data;
        this.id = id;
        this.name = name;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public int getModelVersion() {
        return 0;
    }

    @Override
    public DataType getDataType() {
        return DataType.JSON;
    }

    @Override
    public byte[] getData() {
        return data.getBytes(StandardCharsets.UTF_8);
    }
}
