package ru.citeck.ecos.apps.app;

import org.junit.Test;
import ru.citeck.ecos.apps.app.application.AppVersion;

import static org.junit.Assert.*;

public class VersionTest {

    @Test
    public void test() {

        AppVersion version100 = new AppVersion("1.0.0");
        AppVersion version100_2 = new AppVersion("1.0.0");

        assertEquals(version100, version100_2);
        assertTrue(version100.isAfterOrEqual(version100_2));
        assertTrue(version100.isAfterOrEqual(version100));
        assertTrue(version100_2.isAfterOrEqual(version100));

        AppVersion version1000 = new AppVersion("1.0.0.0");
        assertEquals(version100, version1000);

        assertTrue(version1000.isAfterOrEqual(version100));
        assertTrue(version100.isAfterOrEqual(version1000));

        AppVersion version10001 = new AppVersion("1.0.0.0.1");

        assertTrue(version10001.isAfterOrEqual(version100));
        assertTrue(version10001.isAfterOrEqual(version1000));
        assertFalse(version1000.isAfterOrEqual(version10001));
        assertFalse(version100.isAfterOrEqual(version10001));

        assertEquals(version100.hashCode(), version1000.hashCode());
        assertNotEquals(version100.hashCode(), version10001.hashCode());

        AppVersion version1001000 = new AppVersion("1.0.0.1.0.0.0");
        AppVersion version1001 = new AppVersion("1.0.0.1");

        assertEquals(version1001000.hashCode(), version1001.hashCode());
        assertEquals(version1001000, version1001);

        assertTrue(version1001.isAfterOrEqual(version1001000));
    }

}
