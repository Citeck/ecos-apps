package ru.citeck.ecos.apps.domain.artifact.patch.dto;

import lombok.Data;
import ru.citeck.ecos.apps.artifact.ArtifactRef;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;

@Data
public class ArtifactPatchDto {

    private String id;
    private MLText name;
    private float order;
    private ArtifactRef target;
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public MLText getName() {
        return name;
    }

    public void setName(MLText name) {
        this.name = name;
    }

    public float getOrder() {
        return order;
    }

    public void setOrder(float order) {
        this.order = order;
    }

    public ArtifactRef getTarget() {
        return target;
    }

    public void setTarget(ArtifactRef target) {
        this.target = target;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ObjectData getConfig() {
        return config;
    }

    public void setConfig(ObjectData config) {
        this.config = config;
    }
}
