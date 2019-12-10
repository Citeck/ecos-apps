package ru.citeck.ecos.apps.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class EntityUtils {

    public static <K, V> void changeHibernateSet(Set<V> currentVal, Set<V> newVal, Function<V, K> valToKey) {

        Map<K, V> currentKeysMap = new HashMap<>();
        Map<K, V> newKeysMap = new HashMap<>();

        currentVal.forEach(v -> currentKeysMap.put(valToKey.apply(v), v));
        newVal.forEach(v -> newKeysMap.put(valToKey.apply(v), v));

        currentKeysMap.forEach((key, value) -> {
            if (!newKeysMap.containsKey(key)) {
                currentVal.remove(value);
            }
        });
        newKeysMap.forEach((key, value) -> {
            if (!currentKeysMap.containsKey(key)) {
                currentVal.add(value);
            }
        });
    }
}
