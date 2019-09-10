package ru.citeck.ecos.apps.app;

import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AppUtils {

    public static void extractZip(InputStream in, File destination) throws IOException {

        byte[] buffer = new byte[8192];

        try (ZipInputStream zis = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            ZipEntry ze = zis.getNextEntry();
            while (ze != null){
                try {
                    if (!ze.isDirectory()) {
                        String fileName = ze.getName();
                        File newFile = new File(destination, fileName);
                        newFile.getParentFile().mkdirs();
                        try (FileOutputStream fos = new FileOutputStream(newFile)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                } finally {
                    zis.closeEntry();
                }
                ze = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }

    public static File getTmpDirToExtractApp() {

        String tempDirPath = System.getProperty("java.io.tmpdir");
        if (StringUtils.isBlank(tempDirPath)) {
            tempDirPath = ".";
        }

        File targetDir;
        int count = 0;
        do {
            targetDir = Paths.get(tempDirPath, UUID.randomUUID().toString()).toFile();
        } while (++count < 10 && !targetDir.mkdir());

        if (count == 10) {
            throw new RuntimeException("Temp directory can't be created");
        }

        return targetDir;
    }

    public static Digest getDigest(InputStream in) {

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        byte[] buffer = new byte[8192];
        int count;
        long fullSize = 0;
        try (BufferedInputStream bis = new BufferedInputStream(in)) {
            while ((count = bis.read(buffer)) > 0) {
                fullSize += count;
                digest.update(buffer, 0, count);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error on hex calculating", e);
        }

        return new Digest(bytesToHex(digest.digest()), fullSize);
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
