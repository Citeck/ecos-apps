package ru.citeck.ecos.apps.app.module.patch;

import lombok.Data;
import ru.citeck.ecos.apps.module.ModuleRef;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;

@Data
public class ModulePatchDto {

    private String id;
    private MLText name;
    private float order;
    private ModuleRef target;
    private String type;
    private ObjectData config;

    public ModulePatchDto() {
    }

    public ModulePatchDto(ModulePatchDto other) {

        ModulePatchDto copy = Json.getMapper().copy(other);
        if (copy == null) {
            return;
        }

        this.id = copy.id;
        this.name = copy.name;
        this.order = copy.order;
        this.target = copy.target;
        this.type = copy.type;
        this.config = copy.config;
    }
}
