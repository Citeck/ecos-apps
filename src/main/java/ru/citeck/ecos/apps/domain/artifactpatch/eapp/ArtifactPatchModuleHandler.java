package ru.citeck.ecos.apps.domain.artifactpatch.eapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler;
import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactPatchDto;
import ru.citeck.ecos.apps.domain.artifactpatch.service.ArtifactPatchService;

import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArtifactPatchModuleHandler implements EcosArtifactHandler<ArtifactPatchDto> {

    private final ArtifactPatchService service;

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
    public void listenChanges(@NotNull Consumer<ArtifactPatchDto> consumer) {
        service.addListener(consumer);
    }
}
