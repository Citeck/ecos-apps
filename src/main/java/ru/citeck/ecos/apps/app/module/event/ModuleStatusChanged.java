package ru.citeck.ecos.apps.app.module.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;

@Data
@AllArgsConstructor
public class ModuleStatusChanged {

    private EcosModuleEntity module;
}
