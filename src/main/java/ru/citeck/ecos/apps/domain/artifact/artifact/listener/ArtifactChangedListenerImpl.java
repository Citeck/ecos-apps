package ru.citeck.ecos.apps.domain.artifact.artifact.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService;
import ru.citeck.ecos.apps.eapps.dto.ArtifactUploadDto;
import ru.citeck.ecos.apps.eapps.service.ArtifactUploader;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes {@link EcosArtifactsService#uploadArtifact} from the eapps-command (RabbitMQ) path.
 * The lock has to live here, NOT on {@code uploadArtifact} itself, because the service method is
 * {@code @Transactional}: a lock inside the method body is released before the transaction commits,
 * so two concurrent deliveries of the same {@code (type, ext_id, workspace)} would both pass the
 * {@code getByExtId == null} check and both insert at commit time — second one violates the unique
 * constraint. The lock here is outside the transactional proxy, so it covers the commit too.
 * <p>
 * {@code tryLock(timeout)} (not plain {@code lock()} / {@code synchronized}) so any unexpected
 * hang surfaces as a clear timeout exception instead of a silent stall.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArtifactChangedListenerImpl implements ArtifactUploader {

    private static final long UPLOAD_LOCK_TIMEOUT_SECONDS = 60;

    private final EcosArtifactsService ecosArtifactsService;

    private final ReentrantLock uploadLock = new ReentrantLock();

    @Override
    public void uploadArtifact(@NotNull ArtifactUploadDto uploadDto) {
        boolean acquired;
        try {
            acquired = uploadLock.tryLock(UPLOAD_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                "Interrupted while waiting for the artifact-upload lock; " + describeArtifact(uploadDto), e);
        }
        if (!acquired) {
            throw new IllegalStateException("Timed out (" + UPLOAD_LOCK_TIMEOUT_SECONDS
                + "s) waiting for the artifact-upload lock; " + describeArtifact(uploadDto));
        }
        try {
            ecosArtifactsService.uploadArtifact(uploadDto);
        } finally {
            uploadLock.unlock();
        }
    }

    private static String describeArtifact(ArtifactUploadDto uploadDto) {
        return "type=" + uploadDto.getType()
            + ", workspace=" + uploadDto.getWorkspace()
            + ", source=" + uploadDto.getSource();
    }
}
