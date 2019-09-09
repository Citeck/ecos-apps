package ru.citeck.ecos.apps.app.application;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.regex.Pattern;

public class AppVersion {

    private static final Pattern pattern = Pattern.compile("^\\d+(\\.\\d+)*$");
    private int[] values;
    private String strValue;

    public AppVersion(String value) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("Incorrect version: " + value);
        }
        strValue = value;
        values = Arrays.stream(value.split("."))
                       .mapToInt(Integer::parseInt)
                       .toArray();
    }

    public boolean isAfterOrEqual(AppVersion other) {

        int[] first;
        int[] second;

        if (values.length == other.values.length) {
            first = values;
            second = other.values;
        } else if (values.length > other.values.length) {
            first = values;
            second = new int[values.length];
            System.arraycopy(other.values, 0, second, 0, other.values.length);
        } else {
            first = new int[other.values.length];
            second = other.values;
            System.arraycopy(values, 0, first, 0, values.length);
        }

        for (int i = 0; i < first.length; i++) {
            if (first[i] > second[i]) {
                return true;
            } else if (first[i] < second[i]) {
                return false;
            }
        }

        return true;
    }

    @Override
    @JsonValue
    public String toString() {
        return strValue;
    }
}
