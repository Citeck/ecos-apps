package ru.citeck.ecos.apps.domain.artifact.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.citeck.ecos.apps.domain.artifact.repo.EcosArtifactEntity;

@Data
@AllArgsConstructor
public class ArtifactStatusChanged {

    private EcosArtifactEntity module;
}
