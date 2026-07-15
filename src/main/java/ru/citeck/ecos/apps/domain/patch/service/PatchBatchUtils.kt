package ru.citeck.ecos.apps.domain.patch.service

import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData

object PatchBatchUtils {

    fun isBatched(batch: PatchBatchConfig): Boolean {
        return batch.field.isNotBlank()
    }

    fun buildBatch(config: ObjectData, batch: PatchBatchConfig, offset: Int): BatchSlice {
        val size = if (batch.size <= 0) PatchBatchConfig.DEFAULT_SIZE else batch.size
        val items = config[batch.field]
        val total = if (items.isArray()) items.size() else 0

        val end = minOf(offset + size, total)
        val sliceItems = DataValue.createArr()
        var i = offset
        while (i < end) {
            sliceItems.add(items[i].copy())
            i++
        }
        // Copy only the (small) non-batch fields plus the slice, instead of deep-copying the whole
        // config — including the full batch array — on every batch. Deep-cloning it each time would
        // make the total work O(N^2 / size), defeating the point of batching large imports.
        val thinConfig = ObjectData.create()
        config.forEach { key, value ->
            if (key != batch.field) {
                thinConfig[key] = value.copy()
            }
        }
        thinConfig[batch.field] = sliceItems

        return BatchSlice(thinConfig, end, total, end >= total)
    }

    data class BatchSlice(
        val config: ObjectData,
        val newOffset: Int,
        val total: Int,
        val completed: Boolean
    )
}
