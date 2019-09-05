package ru.citeck.ecos.apps;

import org.junit.Test;
import ru.citeck.ecos.apps.app.AppUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class TestClass {


    @Test
    public void test() throws IOException {

        File zipFile = new File(new File(".").getAbsoluteFile(), "src/test/resources/test-arch.zip");
        //File target = new File(new File(".").getAbsoluteFile(), "src/test/resources/test-arch-result");


        File target = AppUtils.getTmpDirToExtractApp();
        AppUtils.extractZip(new FileInputStream(zipFile), target);

        System.out.println(target.getAbsolutePath());

        /*
        File file = new File(new File(".").getAbsoluteFile(), "src/test/resources/logback.xml");

        InputStream resourceAsStream = new FileInputStream(file);

        System.out.println(AppUtils.getDigest(resourceAsStream));*/

        /*String property = "java.io.tmpdir";

        String tempDir = System.getProperty(property);
        System.out.println("OS current temporary directory is " + tempDir);*/



    }
}
