package ru.citeck.ecos.apps.domain.artifact.artifact.service;

public interface EcosArtifactRev {

    String getId();

    String getRevId();

    String getHash();

    long getSize();

    byte[] getData();
}
