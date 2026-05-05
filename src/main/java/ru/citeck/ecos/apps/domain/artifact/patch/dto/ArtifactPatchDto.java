package ru.citeck.ecos.apps.domain.artifact.patch.dto;

import lombok.Data;
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType;
import ru.citeck.ecos.apps.artifact.ArtifactRef;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.commons.json.serialization.annotation.IncludeNonDefault;

@Data
@IncludeNonDefault
public class ArtifactPatchDto {

    private String id;
    private MLText name;
    private float order;
    private ArtifactRef target;
    /**
     * Workspace the patch applies to. The {@code target} ref stays workspace-less
     * (e.g. {@code ui/menu$admin-workspace-menu}); this field carries the workspace
     * id of the artifact being patched. Empty means a global artifact.
     */
    private String workspace = "";
    private ArtifactSourceType sourceType;
    private String type;
    private ObjectData config;
    private boolean enabled = true;

    public ArtifactPatchDto() {
    }

    public ArtifactPatchDto(ArtifactPatchDto other) {
        this.id = other.id;
        this.name = Json.getMapper().copy(other.name);
        this.order = other.order;
        this.target = other.target;
        this.workspace = other.workspace;
        this.sourceType = other.sourceType;
        this.type = other.type;
        this.config = ObjectData.deepCopy(other.config);
        this.enabled = other.enabled;
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

    public String getWorkspace() {
        return workspace;
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace == null ? "" : workspace;
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

    public ArtifactSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(ArtifactSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
