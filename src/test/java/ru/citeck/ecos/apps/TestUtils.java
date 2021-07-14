package ru.citeck.ecos.apps;

import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.utils.ExceptionUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class TestUtils {

    @SneakyThrows
    public static void assertTrueWhile(Supplier<Boolean> condition, int seconds) {

        long time = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds);
        boolean conditionResult = condition.get();

        while (conditionResult && System.currentTimeMillis() < time) {
            Thread.sleep(100);
            conditionResult = condition.get();
        }

        if (!conditionResult) {
            throw new RuntimeException("Assertion failed. Condition: " + condition);
        }
    }

    @SneakyThrows
    public static void waitWhile(Supplier<Boolean> condition, int timeoutSec) {

        long time = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSec);
        boolean conditionResult = condition.get();

        while (conditionResult && System.currentTimeMillis() < time) {
            Thread.sleep(100);
            conditionResult = condition.get();
        }

        if (conditionResult) {
            throw new RuntimeException("Waiting timeout. Condition: " + condition);
        }
    }

    public static void waitElements(List<?> collection, int count, int seconds) {
        waitWhile(() -> {
            synchronized (collection) {
                return collection.size() != count;
            }
        }, seconds);
    }

    public static void waitElement(List<?> collection, int seconds) {
        waitElements(collection, 1, seconds);
    }
}
