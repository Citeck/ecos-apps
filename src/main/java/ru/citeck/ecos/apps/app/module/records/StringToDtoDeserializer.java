package ru.citeck.ecos.apps.app.module.records;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

public class StringToDtoDeserializer<T> extends StdDeserializer<T> {

    private Class<T> type;
    private ObjectMapper mapper;

    public StringToDtoDeserializer(Class<T> type) {
        this(type, new ObjectMapper());
    }

    public StringToDtoDeserializer(Class<T> type, ObjectMapper mapper) {
        super(type);
        this.type = type;
        this.mapper = mapper;
    }

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

        if (p.currentToken() == JsonToken.START_OBJECT) {
            return mapper.treeToValue(p.readValueAsTree(), type);
        } else if (p.currentToken() == JsonToken.VALUE_STRING) {
            String value = p.getValueAsString();
            if (StringUtils.isBlank(value)) {
                return null;
            }
            return mapper.readValue(value, type);
        } else {
            throw new IllegalStateException("Unknown token: " + p.currentToken());
        }
    }
}
