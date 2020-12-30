package ru.citeck.ecos.apps.domain.artifact.service;

public interface EcosArtifactRev {

    String getId();

    String getRevId();

    String getHash();

    long getSize();

    byte[] getData();
}
