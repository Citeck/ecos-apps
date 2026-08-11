package ru.citeck.ecos.apps.domain.patch.service

import java.time.Instant

/**
 * The light projection of a patch — deliberately NOT [EcosPatchEntity]. That one carries `config`,
 * `state` and `patchResult`, and for an import-data patch `config` holds the whole record array
 * batching exists for. Everything that only orchestrates patches — queueing one for the job, waking
 * the ones that were waiting — reads this instead, so it never drags those megabytes along.
 *
 * Only [executePatch][EcosPatchService] itself needs the full entity: it is the one place that
 * actually runs a patch.
 */
class EcosPatchInfo(
    var id: String = "",
    var patchId: String = "",
    var targetApp: String = "",
    var manual: Boolean = false,
    var status: EcosPatchStatus = EcosPatchStatus.PENDING,
    var batch: PatchBatchConfig = PatchBatchConfig(),
    var nextExecDate: Instant? = null,
    var dependsOn: List<String> = emptyList()
)
