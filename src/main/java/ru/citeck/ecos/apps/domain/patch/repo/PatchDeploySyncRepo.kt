package ru.citeck.ecos.apps.domain.patch.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface PatchDeploySyncRepo : JpaRepository<PatchDeploySyncEntity, Long> {

    fun findBySyncKey(syncKey: String): PatchDeploySyncEntity?

    /**
     * Atomically stamps the deploy watermark, creating the single row on first use. A DB-level
     * upsert (not read-modify-write) so it is safe when called concurrently from different nodes
     * (user-upload path and watcher deploy path) and can't clobber the row with a stale copy.
     */
    @Transactional
    @Modifying
    @Query(
        value = "INSERT INTO ecos_patch_deploy_sync(id, sync_key, deploy_date, patches_sync_date) " +
            "VALUES (nextval('hibernate_sequence'), :key, :date, 0) " +
            "ON CONFLICT (sync_key) DO UPDATE SET deploy_date = :date",
        nativeQuery = true
    )
    fun upsertDeployDate(@Param("key") key: String, @Param("date") date: Long): Int

    /**
     * Marks the watermark reconciled up to [date], but only if no newer deploy arrived meanwhile
     * (the conditional `deployDate = :date`). Returns the number of updated rows — 0 means a deploy
     * bumped the watermark during processing, so the next job tick must reconcile again.
     */
    @Transactional
    @Modifying
    @Query(
        "UPDATE PatchDeploySyncEntity e SET e.patchesSyncDate = :date " +
            "WHERE e.syncKey = :key AND e.deployDate = :date"
    )
    fun markSyncedIfDeployDateUnchanged(@Param("key") key: String, @Param("date") date: Long): Int
}
