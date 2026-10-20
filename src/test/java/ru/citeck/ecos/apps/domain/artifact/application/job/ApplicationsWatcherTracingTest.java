package ru.citeck.ecos.apps.domain.artifact.application.job;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.citeck.ecos.apps.app.domain.status.AppStatus;
import ru.citeck.ecos.apps.app.service.remote.RemoteAppService;
import ru.citeck.ecos.apps.app.service.remote.RemoteAppStatus;
import ru.citeck.ecos.apps.domain.artifact.application.service.EcosApplicationsService;
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService;
import ru.citeck.ecos.apps.domain.artifact.patch.service.EcosArtifactsPatchService;
import ru.citeck.ecos.apps.domain.artifact.source.service.EcosArtifactsSourcesService;
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService;
import ru.citeck.ecos.micrometer.EcosMicrometerContext;
import ru.citeck.ecos.micrometer.obs.EcosObs;
import ru.citeck.ecos.webapp.api.EcosWebAppApi;
import ru.citeck.ecos.webapp.lib.lock.EcosAppLockService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ApplicationsWatcherTracingTest {

    private final ObservationRegistry registry = ObservationRegistry.create();
    private final List<String> events = new ArrayList<>();
    private final RemoteAppService remote = mock(RemoteAppService.class);
    private final EcosApplicationsService applications = mock(EcosApplicationsService.class);
    private final EcosArtifactsService artifacts = mock(EcosArtifactsService.class);
    private final EcosArtifactsSourcesService sources = mock(EcosArtifactsSourcesService.class);
    private final EcosArtifactTypesService types = mock(EcosArtifactTypesService.class);
    private ApplicationsWatcherJob job;
    private AtomicBoolean closed;

    @BeforeEach
    void setUp() {
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onStart(Observation.Context context) {
                events.add("start:" + context.getName());
            }

            @Override
            public void onStop(Observation.Context context) {
                events.add("stop:" + context.getName());
            }
        });
        EcosMicrometerContext meter = mock(EcosMicrometerContext.class);
        when(meter.createObs("ecos.apps.watcher")).thenAnswer(invocation ->
            new EcosObs(Observation.createNotStarted("ecos.apps.watcher", registry)));
        job = new ApplicationsWatcherJob(applications, sources, types, artifacts,
            mock(EcosArtifactsPatchService.class), meter, mock(EcosWebAppApi.class),
            mock(EcosAppLockService.class), remote);
        closed = (AtomicBoolean) ReflectionTestUtils.getField(job, "isContextClosed");
        when(types.getLastModified()).thenReturn(Instant.EPOCH);
        when(sources.getLastModified()).thenReturn(Instant.EPOCH);
        when(artifacts.getLastModifiedTime()).thenReturn(Instant.EPOCH);
    }

    @Test
    void broadcastCompletesBeforeObservationButStatusAndArtifactUpdatesAreObserved() {
        RemoteAppStatus status = new RemoteAppStatus("emodel", "model-1",
            new AppStatus(Instant.EPOCH, List.of(), List.of(), Instant.EPOCH));
        when(remote.getAppsStatus()).thenAnswer(invocation -> {
            assertThat(registry.getCurrentObservation()).isNull();
            assertThat(events).isEmpty();
            events.add("broadcast-completed");
            return List.of(status);
        });
        doAnswer(invocation -> {
            assertThat(registry.getCurrentObservation().getContext().getName()).isEqualTo("ecos.apps.watcher");
            events.add("update-apps");
            return null;
        }).when(applications).updateApps(anyList());
        doAnswer(invocation -> {
            assertThat(registry.getCurrentObservation().getContext().getName()).isEqualTo("ecos.apps.watcher");
            events.add("update-artifacts");
            closed.set(true); // End the cycle without the subsequent five-second polling delay.
            return null;
        }).when(artifacts).updateFailedArtifacts();

        ReflectionTestUtils.invokeMethod(job, "doWatcherJob");

        assertThat(events).containsExactly("broadcast-completed", "start:ecos.apps.watcher",
            "update-apps", "update-artifacts", "stop:ecos.apps.watcher");
        verify(applications).updateApps(List.of(status));
        assertThat(job.getActiveApps()).containsExactly("emodel");
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void shutdownDuringBroadcastPreventsApplicationAndArtifactUpdates() {
        when(remote.getAppsStatus()).thenAnswer(invocation -> {
            closed.set(true);
            return List.of();
        });

        ReflectionTestUtils.invokeMethod(job, "doWatcherJob");

        verify(remote).getAppsStatus();
        verifyNoInteractions(applications, artifacts, sources, types);
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void shutdownBeforeCycleSkipsBroadcastAndObservation() {
        closed.set(true);

        ReflectionTestUtils.invokeMethod(job, "doWatcherJob");

        verifyNoInteractions(remote, applications, artifacts);
        assertThat(events).isEmpty();
    }
}
