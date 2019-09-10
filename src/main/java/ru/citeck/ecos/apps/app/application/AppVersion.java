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
        values = Arrays.stream(value.split("\\."))
                       .mapToInt(Integer::parseInt)
                       .toArray();
    }

    public boolean isAfterOrEqual(AppVersion other) {

        int maxSize = Math.max(other.values.length, values.length);

        int[] first = getIntValue(maxSize);
        int[] second = other.getIntValue(maxSize);

        for (int i = 0; i < first.length; i++) {
            if (first[i] > second[i]) {
                return true;
            } else if (first[i] < second[i]) {
                return false;
            }
        }

        return true;
    }

    private int[] getIntValue(int size) {
        if (values.length == size) {
            return values;
        }
        int[] result = new int[size];
        System.arraycopy(values, 0, result, 0, values.length);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AppVersion version = (AppVersion) o;
        int maxSize = Math.max(version.values.length, values.length);

        return Arrays.equals(getIntValue(maxSize), version.getIntValue(maxSize));
    }

    @Override
    public int hashCode() {

        int result = 0;

        int lastIdx = values.length - 1;
        while (lastIdx >= 0 && values[lastIdx] == 0) {
            lastIdx--;
        }

        if (lastIdx == -1) {
            return 0;
        }

        for (int i = 0; i <= lastIdx; i++) {
            result = 31 * result + Integer.hashCode(values[i]);
        }

        return result;
    }

    @Override
    @JsonValue
    public String toString() {
        return strValue;
    }
}
