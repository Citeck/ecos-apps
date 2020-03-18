package ru.citeck.ecos.apps.app.module;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EcosModule {
    private final Object data;
    private final String type;
}
