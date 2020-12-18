package ru.citeck.ecos.apps.domain.module.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.citeck.ecos.apps.domain.module.repo.EcosModuleEntity;

@Data
@AllArgsConstructor
public class ModuleStatusChanged {

    private EcosModuleEntity module;
}
