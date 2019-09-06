package ru.citeck.ecos.apps.app.module;

import lombok.Getter;
import org.apache.commons.io.IOUtils;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;

import java.io.IOException;
import java.sql.SQLException;

public class EcosModuleDb implements EcosModuleRev {

    @Getter String id;
    @Getter String revId;
    @Getter String type;
    @Getter String name;
    @Getter int modelVersion;
    @Getter String mimetype;
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
        this.mimetype = entity.getMimetype();
        try {
            this.data = IOUtils.toByteArray(entity.getData().getBinaryStream());
        } catch (IOException | SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
