package ru.citeck.ecos.apps.domain.module.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EcosModule {
    private final Object data;
    private final String type;
}
