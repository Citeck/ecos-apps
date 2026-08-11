package ru.citeck.ecos.apps.domain.patch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.apps.EcosAppsApp
import ru.citeck.ecos.apps.domain.patch.repo.PatchDeploySyncRepo
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

/**
 * Validates the deploy-watermark SQL that guarantees no DEPS_WAITING wake is lost: the atomic
 * create-or-bump upsert, and the conditional "mark synced" that must reject a stale observed deploy
 * date (a deploy landed while the patch job was scanning) and accept the current one.
 *
 * Uses an isolated sync_key so it can't race with the live singleton watermark that the running
 * patch job maintains inside the same booted app.
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosAppsApp::class])
class PatchDeploySyncTest {

    @Autowired
    lateinit var repo: PatchDeploySyncRepo

    @Test
    fun markSyncedIsConditionalOnObservedDeployDate() {
        // Isolated key + assertions only on affected-row counts => deterministic and idempotent
        // across reruns regardless of any row left by a previous run.
        val key = "test-cas-key"

        // First upsert creates the row; second bumps the existing one (ON CONFLICT path).
        repo.upsertDeployDate(key, 100)
        repo.upsertDeployDate(key, 200)

        // Stale observed date (a newer deploy already advanced the watermark) -> rejected, so the
        // watermark stays out-of-sync and the next job tick reconciles again.
        assertThat(repo.markSyncedIfDeployDateUnchanged(key, 100)).isZero()

        // Current observed date -> accepted.
        assertThat(repo.markSyncedIfDeployDateUnchanged(key, 200)).isEqualTo(1)
    }
}
