package ru.citeck.ecos.apps.domain.module.eapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.domain.module.dto.ModulePatchDto;
import ru.citeck.ecos.apps.domain.modulepatch.service.ModulePatchService;
import ru.citeck.ecos.apps.module.handler.EcosModuleHandler;
import ru.citeck.ecos.apps.module.handler.ModuleMeta;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;

import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModulePatchModuleHandler implements EcosModuleHandler<ModulePatchDto> {

    private final ModulePatchService service;

    @Override
    public void deployModule(@NotNull ModulePatchDto modulePatch) {
        service.save(modulePatch);
    }

    @NotNull
    @Override
    public ModuleWithMeta<ModulePatchDto> getModuleMeta(@NotNull ModulePatchDto modulePatch) {
        return new ModuleWithMeta<>(modulePatch, new ModuleMeta(modulePatch.getId()));
    }

    @NotNull
    @Override
    public String getModuleType() {
        return "app/module-patch";
    }

    @Override
    public void listenChanges(@NotNull Consumer<ModulePatchDto> consumer) {
        service.addListener(consumer);
    }

    @Nullable
    @Override
    public ModuleWithMeta<ModulePatchDto> prepareToDeploy(@NotNull ModulePatchDto modulePatch) {
        return getModuleMeta(modulePatch);
    }
}
