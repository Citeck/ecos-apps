package ru.citeck.ecos.apps.domain.artifact.patch.eapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler;
import ru.citeck.ecos.apps.domain.artifact.patch.dto.ArtifactPatchDto;
import ru.citeck.ecos.apps.domain.artifact.patch.service.EcosArtifactsPatchService;

import java.util.function.Consumer;

/**
 * @deprecated artifact handler to support legacy
 *             artifacts with type 'app/module-patch'.
 *             New type: 'app/artifact-patch'
 */
@Slf4j
@Component
@Deprecated
@RequiredArgsConstructor
public class ModulePatchArtifactHandler implements EcosArtifactHandler<ArtifactPatchDto> {

    private final EcosArtifactsPatchService service;

    @Override
    public void deployArtifact(@NotNull ArtifactPatchDto modulePatch) {
        service.save(modulePatch);
    }

    @NotNull
    @Override
    public String getArtifactType() {
        return "app/module-patch";
    }

    @Override
    public void listenChanges(@NotNull Consumer<ArtifactPatchDto> consumer) {}
}
