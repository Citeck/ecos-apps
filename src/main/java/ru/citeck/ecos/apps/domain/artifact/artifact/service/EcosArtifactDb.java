package ru.citeck.ecos.apps.domain.artifact.artifact.service;

import lombok.Getter;
import ru.citeck.ecos.apps.domain.artifact.artifact.repo.EcosArtifactEntity;
import ru.citeck.ecos.apps.domain.artifact.artifact.repo.EcosArtifactRevEntity;
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity;

public class EcosArtifactDb implements EcosArtifactRev {

    @Getter String id;
    @Getter String revId;
    @Getter String type;
    @Getter int modelVersion;
    @Getter String hash;
    @Getter byte[] data;
    @Getter long size;

    public EcosArtifactDb(EcosArtifactRevEntity entity) {

        EcosArtifactEntity artifact = entity.getArtifact();
        EcosContentEntity content = entity.getContent();

        this.id = artifact.getExtId();
        this.revId = entity.getExtId();
        this.type = artifact.getType();

        Integer version = entity.getModelVersion();
        this.modelVersion = version != null ? version : 0;

        this.data = content.getData();
        this.hash = content.getHash();
        this.size = content.getSize();
    }
}
