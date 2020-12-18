package ru.citeck.ecos.apps.domain.module.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.apps.domain.common.dto.DeployStatus;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ModulePublishState {
    private DeployStatus status;
    private String msg;
}

