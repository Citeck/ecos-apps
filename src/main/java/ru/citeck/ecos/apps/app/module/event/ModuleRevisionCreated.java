package ru.citeck.ecos.apps.app.module.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;

@Data
@AllArgsConstructor
public class ModuleRevisionCreated {

    private String type;
    private String id;
}
