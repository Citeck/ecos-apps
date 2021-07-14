package ru.citeck.ecos.apps.domain.common.repo.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class EntityUtils {

    private EntityUtils() {
    }

    public static <K, V> boolean changeHibernateSet(Set<V> currentVal, Set<V> newVal, Function<V, K> valToKey) {

        Map<K, V> currentKeysMap = new HashMap<>();
        Map<K, V> newKeysMap = new HashMap<>();

        currentVal.forEach(v -> currentKeysMap.put(valToKey.apply(v), v));
        newVal.forEach(v -> newKeysMap.put(valToKey.apply(v), v));

        AtomicBoolean wasChanged = new AtomicBoolean();

        currentKeysMap.forEach((key, value) -> {
            if (!newKeysMap.containsKey(key)) {
                currentVal.remove(value);
                wasChanged.set(true);
            }
        });
        newKeysMap.forEach((key, value) -> {
            if (!currentKeysMap.containsKey(key)) {
                currentVal.add(value);
                wasChanged.set(true);
            }
        });
        return wasChanged.get();
    }
}
