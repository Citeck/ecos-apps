package ru.citeck.ecos.apps.domain.artifact.listener;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.domain.artifact.service.EcosArtifactsService;
import ru.citeck.ecos.apps.eapps.listener.ArtifactChangedListener;
import ru.citeck.ecos.commands.CommandsServiceFactory;
import ru.citeck.ecos.commands.context.CommandCtxManager;

import javax.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
public class ArtifactChangedListenerImpl implements ArtifactChangedListener {

    private final EcosArtifactsService ecosArtifactsService;
    private final CommandsServiceFactory commandsServiceFactory;

    private CommandCtxManager commandCtxManager;

    @PostConstruct
    public void init() {
        commandCtxManager = commandsServiceFactory.getCommandCtxManager();
    }

    @Override
    public void onArtifactChanged(@NotNull String s, @NotNull Object o) {
        //todo
        //ecosArtifactsService.uploadUserArtifact(commandCtxManager.getSourceAppName(), module, type);
    }
}
