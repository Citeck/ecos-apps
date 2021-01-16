package ru.citeck.ecos.apps.domain.artifact.artifact.listener;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService;
import ru.citeck.ecos.apps.eapps.dto.ArtifactUploadDto;
import ru.citeck.ecos.apps.eapps.service.ArtifactUploader;

@Component
@RequiredArgsConstructor
public class ArtifactChangedListenerImpl implements ArtifactUploader {

    private final EcosArtifactsService ecosArtifactsService;

    @Override
    public void uploadArtifact(@NotNull ArtifactUploadDto uploadDto) {
        ecosArtifactsService.uploadArtifact(uploadDto);
    }
}
