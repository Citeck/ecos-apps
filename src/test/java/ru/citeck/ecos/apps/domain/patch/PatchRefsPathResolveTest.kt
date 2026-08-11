package ru.citeck.ecos.apps.domain.patch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.citeck.ecos.apps.domain.patch.service.PatchRefsDeployChecker
import ru.citeck.ecos.commons.data.ObjectData

class PatchRefsPathResolveTest {

    @Test
    fun resolveTopLevelRef() {
        val cfg = ObjectData.create().set("typeRef", "emodel/type@t")
        val refs = PatchRefsDeployChecker.resolveRefs(cfg, listOf("typeRef"))
        assertThat(refs.map { it.toString() }).containsExactly("emodel/type@t")
    }

    @Test
    fun resolveNestedDotPath() {
        val cfg = ObjectData.create().set("a", ObjectData.create().set("ref", "emodel/type@x"))
        val refs = PatchRefsDeployChecker.resolveRefs(cfg, listOf("a.ref"))
        assertThat(refs.map { it.toString() }).containsExactly("emodel/type@x")
    }

    @Test
    fun resolveListOfRefs() {
        val cfg = ObjectData.create().set("refs", listOf("emodel/type@a", "emodel/type@b"))
        val refs = PatchRefsDeployChecker.resolveRefs(cfg, listOf("refs"))
        assertThat(refs.map { it.toString() }).containsExactly("emodel/type@a", "emodel/type@b")
    }

    @Test
    fun blankOrMissingPathYieldsNoRefs() {
        val cfg = ObjectData.create().set("typeRef", "")
        assertThat(PatchRefsDeployChecker.resolveRefs(cfg, listOf("typeRef", "missing"))).isEmpty()
    }
}
