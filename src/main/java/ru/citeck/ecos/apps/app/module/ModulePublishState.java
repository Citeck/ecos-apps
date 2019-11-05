package ru.citeck.ecos.apps.app.module;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.apps.app.PublishStatus;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ModulePublishState {
    private PublishStatus status;
    private String msg;
}

