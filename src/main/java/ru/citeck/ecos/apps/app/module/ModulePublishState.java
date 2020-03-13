package ru.citeck.ecos.apps.app.module;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.apps.app.DeployStatus;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ModulePublishState {
    private DeployStatus status;
    private String msg;
}

