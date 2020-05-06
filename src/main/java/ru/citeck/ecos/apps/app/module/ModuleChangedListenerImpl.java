package ru.citeck.ecos.apps.app.module;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;
import ru.citeck.ecos.apps.spring.module.ModuleChangedListener;
import ru.citeck.ecos.commands.CommandsServiceFactory;
import ru.citeck.ecos.commands.context.CommandCtxManager;

import javax.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
public class ModuleChangedListenerImpl implements ModuleChangedListener {

    private final EcosModuleService ecosModuleService;
    private final CommandsServiceFactory commandsServiceFactory;

    private CommandCtxManager commandCtxManager;

    @PostConstruct
    public void init() {
        commandCtxManager = commandsServiceFactory.getCommandCtxManager();
    }

    @Override
    public void onChanged(@NotNull String type, @NotNull ModuleWithMeta<Object> module) {
        ecosModuleService.uploadUserModule(commandCtxManager.getSourceAppName(), module, type);
    }
}
