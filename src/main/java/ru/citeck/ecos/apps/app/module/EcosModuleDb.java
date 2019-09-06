package ru.citeck.ecos.apps.app.module;

import lombok.Getter;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;

public class EcosModuleDb implements EcosModuleRev {

    @Getter String id;
    @Getter String revId;
    @Getter String type;
    @Getter String name;
    @Getter int modelVersion;
    @Getter DataType dataType;
    @Getter String hash;
    @Getter byte[] data;
    @Getter long size;

    public EcosModuleDb(EcosModuleRevEntity entity) {

        EcosModuleEntity module = entity.getModule();

        this.id = module.getExtId();
        this.revId = entity.getExtId();
        this.type = module.getType();
        this.name = entity.getName();
        this.modelVersion = entity.getModelVersion();
        this.dataType = entity.getDataType();
        this.data = entity.getData();
    }
}
