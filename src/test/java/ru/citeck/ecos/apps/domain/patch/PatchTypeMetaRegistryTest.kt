package ru.citeck.ecos.apps.domain.patch

import io.github.oshai.kotlinlogging.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.apps.EcosAppsApp
import ru.citeck.ecos.webapp.lib.patch.PatchTypeMetaRegistry
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

/**
 * Focused integration test for the ZK-backed [PatchTypeMetaRegistry] consumer side.
 * The happy-path (eapps publishing its meta and reading it back) is already exercised
 * indirectly by [ImportDataPatchTest] / [ImportDataBatchingTest]. What's not covered
 * elsewhere is the "unregistered app" case, which is exactly what makes the patch
 * deploy-gate (see EcosPatchService / PatchRefsDeployChecker) DEFER instead of proceeding.
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosAppsApp::class])
class PatchTypeMetaRegistryTest {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Autowired
    lateinit var registry: PatchTypeMetaRegistry

    @Test
    fun test() {
        val waitingStart = System.currentTimeMillis()
        while (!registry.isAppRegistered("eapps")) {
            if ((System.currentTimeMillis() - waitingStart) > 60_000) {
                error("Timeout waiting for 'eapps' to register its patch type meta in ZK")
            }
            Thread.sleep(1000)
        }
        log.info { "'eapps' registered after " + (System.currentTimeMillis() - waitingStart) + "ms" }

        // registered app + type with declared meta -> meta round-trips through ZK
        assertThat(registry.getMeta("eapps", "import-data").dependsOnRefs).isEqualTo(listOf("typeRef"))

        // registered app + type with no meta (default/empty, filtered out of the published map)
        assertThat(registry.getMeta("eapps", "mutate").dependsOnRefs).isEmpty()

        // unregistered app -> this is the case that makes the patch deploy-gate DEFER
        assertThat(registry.isAppRegistered("nonexistent-app-xyz")).isFalse()
        assertThat(registry.getMeta("nonexistent-app-xyz", "import-data").dependsOnRefs).isEmpty()
    }
}
