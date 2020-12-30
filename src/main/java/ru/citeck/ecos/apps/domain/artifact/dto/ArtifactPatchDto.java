package ru.citeck.ecos.apps.domain.artifact.dto;

import lombok.Data;
import ru.citeck.ecos.apps.module.ModuleRef;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;

@Data
public class ArtifactPatchDto {

    private String id;
    private MLText name;
    private float order;
    private ModuleRef target;
    private String type;
    private ObjectData config;

    public ArtifactPatchDto() {
    }

    public ArtifactPatchDto(ArtifactPatchDto other) {
        this.id = other.id;
        this.name = Json.getMapper().copy(other.name);
        this.order = other.order;
        this.target = other.target;
        this.type = other.type;
        this.config = ObjectData.deepCopy(other.config);
    }
}
