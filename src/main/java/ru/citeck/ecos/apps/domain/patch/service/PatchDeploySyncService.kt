package ru.citeck.ecos.apps.domain.patch.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.apps.domain.patch.repo.PatchDeploySyncRepo

/**
 * Bridges artifact deployment and the patch job through a persisted watermark
 * ([PatchDeploySyncEntity]), replacing an in-memory "last deploy" signal that would neither be
 * transactional with the deploy nor visible to a patch job running on another node.
 */
@Service
class PatchDeploySyncService(
    private val repo: PatchDeploySyncRepo
) {

    companion object {
        private const val SYNC_KEY = "deploy"
    }

    data class State(val deployDate: Long, val patchesSyncDate: Long) {
        fun isOutOfSync(): Boolean {
            return deployDate != patchesSyncDate
        }
    }

    /**
     * Bumps the deploy watermark (creating the row on first use). Meant to be called from within the
     * deploy transaction (on every artifact -> DEPLOYED transition), so the timestamp advances
     * atomically with the deploy commit.
     */
    @Transactional
    fun markArtifactsDeployed() {
        repo.upsertDeployDate(SYNC_KEY, System.currentTimeMillis())
    }

    @Transactional(readOnly = true)
    fun getState(): State {
        val row = repo.findBySyncKey(SYNC_KEY) ?: return State(0, 0)
        return State(row.deployDate, row.patchesSyncDate)
    }

    /**
     * Marks the watermark reconciled up to [observedDeployDate] (the deploy date captured before the
     * patch job started processing). Fails (returns false) if a deploy bumped the watermark
     * meanwhile, leaving the row out-of-sync so the next tick reconciles again.
     */
    @Transactional
    fun markSynced(observedDeployDate: Long): Boolean {
        return repo.markSyncedIfDeployDateUnchanged(SYNC_KEY, observedDeployDate) > 0
    }
}
