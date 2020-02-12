package ru.citeck.ecos.apps.app.module;

import lombok.Getter;
import ru.citeck.ecos.apps.domain.EcosContentEntity;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;

public class EcosModuleDb implements EcosModuleRev {

    @Getter String id;
    @Getter String revId;
    @Getter String type;
    @Getter int modelVersion;
    @Getter String hash;
    @Getter byte[] data;
    @Getter long size;

    public EcosModuleDb(EcosModuleRevEntity entity) {

        EcosModuleEntity module = entity.getModule();
        EcosContentEntity content = entity.getContent();

        this.id = module.getExtId();
        this.revId = entity.getExtId();
        this.type = module.getType();

        Integer version = entity.getModelVersion();
        this.modelVersion = version != null ? version : 0;

        this.data = content.getData();
        this.hash = content.getHash();
        this.size = content.getSize();
    }
}