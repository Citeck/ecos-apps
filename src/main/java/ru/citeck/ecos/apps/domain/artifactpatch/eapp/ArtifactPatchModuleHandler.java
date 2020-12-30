package ru.citeck.ecos.apps.domain.artifactpatch.eapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactPatchDto;
import ru.citeck.ecos.apps.domain.artifactpatch.service.ArtifactPatchService;
import ru.citeck.ecos.apps.module.handler.EcosModuleHandler;
import ru.citeck.ecos.apps.module.handler.ModuleMeta;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;

import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArtifactPatchModuleHandler implements EcosModuleHandler<ArtifactPatchDto> {

    private final ArtifactPatchService service;

    @Override
    public void deployModule(@NotNull ArtifactPatchDto modulePatch) {
        service.save(modulePatch);
    }

    @NotNull
    @Override
    public ModuleWithMeta<ArtifactPatchDto> getModuleMeta(@NotNull ArtifactPatchDto modulePatch) {
        return new ModuleWithMeta<>(modulePatch, new ModuleMeta(modulePatch.getId()));
    }

    @NotNull
    @Override
    public String getModuleType() {
        return "app/module-patch";
    }

    @Override
    public void listenChanges(@NotNull Consumer<ArtifactPatchDto> consumer) {
        service.addListener(consumer);
    }

    @Nullable
    @Override
    public ModuleWithMeta<ArtifactPatchDto> prepareToDeploy(@NotNull ArtifactPatchDto modulePatch) {
        return getModuleMeta(modulePatch);
    }
}
