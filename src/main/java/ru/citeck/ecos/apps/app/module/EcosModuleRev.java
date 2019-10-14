package ru.citeck.ecos.apps.app.module;

public interface EcosModuleRev {

    String getId();

    String getRevId();

    String getHash();

    long getSize();

    byte[] getData();
}
