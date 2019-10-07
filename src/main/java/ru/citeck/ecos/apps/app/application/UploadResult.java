package ru.citeck.ecos.apps.app.application;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.apps.domain.EcosAppRevEntity;

@Data
@AllArgsConstructor
@NoArgsConstructor
class UploadResult {
    private EcosAppRevEntity appRevEntity;
    private boolean uploaded;
}
