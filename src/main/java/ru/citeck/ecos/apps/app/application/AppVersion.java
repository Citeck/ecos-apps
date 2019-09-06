package ru.citeck.ecos.apps.app.application;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.regex.Pattern;

public class AppVersion {

    private static final Pattern pattern = Pattern.compile("^\\d+(\\.\\d+)*$");
    private String value;

    public AppVersion(String value) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("Incorrect version: " + value);
        }
        this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
        return value;
    }
}
