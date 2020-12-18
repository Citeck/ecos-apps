package ru.citeck.ecos.apps.domain.module.service;

public interface EcosModuleRev {

    String getId();

    String getRevId();

    String getHash();

    long getSize();

    byte[] getData();
}
