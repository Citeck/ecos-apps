package ru.citeck.ecos.apps.domain.patch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.citeck.ecos.apps.domain.patch.service.PatchBatchConfig
import ru.citeck.ecos.apps.domain.patch.service.PatchBatchUtils
import ru.citeck.ecos.commons.data.ObjectData

class PatchBatchUtilsTest {

    private fun batch(field: String, size: Int) = PatchBatchConfig(field, size)

    private fun configWith(vararg ids: Int): ObjectData {
        val recs = ids.map { ObjectData.create().set("code", it) }
        return ObjectData.create().set("records", recs).set("typeRef", "emodel/type@t")
    }

    @Test
    fun isBatchedTrueOnlyWhenFieldPresent() {
        assertThat(PatchBatchUtils.isBatched(batch("records", 20))).isTrue()
        assertThat(PatchBatchUtils.isBatched(PatchBatchConfig())).isFalse()
    }

    @Test
    fun buildBatchSlicesAndKeepsSharedConfig() {
        val slice = PatchBatchUtils.buildBatch(configWith(1, 2, 3, 4, 5), batch("records", 2), 0)
        assertThat(slice.total).isEqualTo(5)
        assertThat(slice.newOffset).isEqualTo(2)
        assertThat(slice.completed).isFalse()
        assertThat(slice.config["records"].size()).isEqualTo(2)
        assertThat(slice.config["records"][0]["code"].asInt()).isEqualTo(1)
        assertThat(slice.config["typeRef"].asText()).isEqualTo("emodel/type@t")
    }

    @Test
    fun buildBatchLastSliceSetsCompleted() {
        val slice = PatchBatchUtils.buildBatch(configWith(1, 2, 3, 4, 5), batch("records", 2), 4)
        assertThat(slice.config["records"].size()).isEqualTo(1)
        assertThat(slice.newOffset).isEqualTo(5)
        assertThat(slice.completed).isTrue()
    }

    @Test
    fun buildBatchDefaultSizeIs20() {
        val cfg = configWith(*IntArray(25) { it }.toTypedArray().toIntArray())
        val slice = PatchBatchUtils.buildBatch(cfg, PatchBatchConfig(field = "records"), 0)
        assertThat(slice.config["records"].size()).isEqualTo(20)
    }

    @Test
    fun buildBatchEmptyListCompletesImmediately() {
        val slice = PatchBatchUtils.buildBatch(configWith(), batch("records", 2), 0)
        assertThat(slice.total).isEqualTo(0)
        assertThat(slice.completed).isTrue()
        assertThat(slice.isEmpty).isTrue()
    }

    @Test
    fun buildBatchOffsetAtTheEndGivesEmptySlice() {
        val slice = PatchBatchUtils.buildBatch(configWith(1, 2, 3), batch("records", 2), 3)
        assertThat(slice.config["records"].size()).isEqualTo(0)
        assertThat(slice.newOffset).isEqualTo(3)
        assertThat(slice.completed).isTrue()
        assertThat(slice.isEmpty).isTrue()
    }

    @Test
    fun buildBatchNonEmptySliceIsNotEmpty() {
        assertThat(PatchBatchUtils.buildBatch(configWith(1, 2, 3), batch("records", 2), 0).isEmpty).isFalse()
        assertThat(PatchBatchUtils.buildBatch(configWith(1, 2, 3), batch("records", 2), 2).isEmpty).isFalse()
    }

    @Test
    fun buildBatchSliceElementsDoNotAliasOriginalConfig() {
        val config = configWith(1, 2, 3, 4, 5)
        val slice = PatchBatchUtils.buildBatch(config, batch("records", 2), 0)

        slice.config["records"][0]["code"] = 999

        assertThat(config["records"][0]["code"].asInt()).isEqualTo(1)
        assertThat(slice.config["records"][0]["code"].asInt()).isEqualTo(999)
    }
}
