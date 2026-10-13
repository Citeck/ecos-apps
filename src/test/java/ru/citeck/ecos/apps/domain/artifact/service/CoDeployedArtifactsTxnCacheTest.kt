package ru.citeck.ecos.apps.domain.artifact.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import ru.citeck.ecos.apps.app.domain.handler.ArtifactDeployMeta
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.ArtifactRevSourceType
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.DeployStatus
import ru.citeck.ecos.apps.domain.artifact.artifact.repo.EcosArtifactEntity
import ru.citeck.ecos.apps.domain.artifact.artifact.repo.EcosArtifactRevEntity
import ru.citeck.ecos.apps.domain.artifact.artifact.repo.EcosArtifactsRepo
import ru.citeck.ecos.apps.domain.artifact.artifact.service.DeployError
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.artifact.service.deploy.ArtifactDeployer
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity
import ru.citeck.ecos.test.commons.EcosWebAppApiMock
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.txn.lib.manager.EcosTxnProps
import ru.citeck.ecos.txn.lib.manager.TransactionManager
import ru.citeck.ecos.txn.lib.manager.TransactionManagerImpl
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Instant

class CoDeployedArtifactsTxnCacheTest {

    private lateinit var manager: TransactionManagerImpl
    private var previousManager: TransactionManager? = null
    private lateinit var service: EcosArtifactsService
    private lateinit var repo: EcosArtifactsRepo
    private lateinit var types: EcosArtifactTypesService
    private var inventory = emptyList<EcosArtifactEntity>()
    private var deploying = emptyList<EcosArtifactEntity>()
    private var inventoryReads = 0
    private var nextId = 0L

    @BeforeEach
    fun setUp() {
        previousManager = runCatching { TxnContext.getManager() }.getOrNull()
        manager = TransactionManagerImpl()
        manager.init(EcosWebAppApiMock("co-deployed-cache-test"), EcosTxnProps())
        TxnContext.setManager(manager)
        repo = mock()
        types = mock()
        service = EcosArtifactsService(
            mock(), mock(), repo, mock(), mock(), mock(), types, mock(), mock(), mock(), emptyList()
        )
        whenever(repo.findAllByTypeAndDeployStatus(any(), any(), any(), any())).thenAnswer { deploying }
        whenever(repo.save(any<EcosArtifactEntity>())).thenAnswer { it.getArgument<EcosArtifactEntity>(0) }
        whenever(repo.getArtifactsByEcosAppAndWorkspace(any(), any())).thenAnswer {
            inventoryReads++
            val app = it.getArgument<String>(0)
            val workspace = it.getArgument<String>(1)
            inventory.filter { entity -> entity.ecosApp == app && entity.workspace == workspace }
        }
        whenever(types.getArtifactRecordRef(any(), any())).thenAnswer {
            EntityRef.valueOf("emodel/type@${it.getArgument<String>(1)}")
        }
    }

    @AfterEach
    fun tearDown() {
        if (previousManager != null) {
            TxnContext.setManager(previousManager!!)
        } else {
            // Restore the uninitialized global facade when this test ran without a Spring context.
            ReflectionTestUtils.setField(TxnContext, "manager", null)
            ReflectionTestUtils.setField(TxnContext, "managerExists", false)
        }
        manager.shutdown()
    }

    private fun artifact(id: String, app: String = "app", workspace: String = "workspace") = EcosArtifactEntity().apply entity@{
        this.id = ++nextId
        type = "model/type"
        extId = id
        ecosApp = app
        this.workspace = workspace
        lastRev = EcosArtifactRevEntity().apply {
            artifact = this@entity
            sourceType = ArtifactRevSourceType.ECOS_APP
            sourceId = app
            content = EcosContentEntity().apply { data = byteArrayOf() }
        }
    }

    private fun deploy(vararg artifacts: EcosArtifactEntity): List<List<EntityRef>> {
        deploying = artifacts.toList()
        deploying.forEach { it.deployStatus = DeployStatus.DRAFT }
        val refs = mutableListOf<List<EntityRef>>()
        val deployer = object : ArtifactDeployer {
            override fun getSupportedTypes() = listOf("model/type")

            override fun deploy(type: String, artifact: ByteArray, meta: ArtifactDeployMeta): List<DeployError> {
                refs.add(meta.coDeployedArtifacts)
                return emptyList()
            }
        }
        assertThat(service.deployArtifacts(deployer, Instant.now())).isTrue()
        return refs
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun refreshesAfterSiblingAddedOrRemoved(initiallyPresent: Boolean) {
        val first = artifact("first")
        val sibling = artifact("sibling")
        inventory = if (initiallyPresent) listOf(first, sibling) else listOf(first)
        TxnContext.doInTxn {
            TxnContext.doInTxn(readOnly = true) {
                assertThat(deploy(first).single()).hasSize(if (initiallyPresent) 2 else 1)
            }
            inventory = if (initiallyPresent) listOf(first) else listOf(first, sibling)
            TxnContext.doInTxn(readOnly = true) {
                assertThat(deploy(first).single()).containsExactlyElementsOf(
                    inventory.map { EntityRef.valueOf("emodel/type@${it.extId}") }
                )
            }
        }
        assertThat(inventoryReads).isEqualTo(2)
    }

    @Test
    fun reusesReadOnlyBatchAndSeparatesAppsAndWorkspaces() {
        val first = artifact("first")
        val sibling = artifact("sibling")
        val otherApp = artifact("other-app", app = "other")
        val otherWorkspace = artifact("other-workspace", workspace = "other")
        inventory = listOf(first, sibling, otherApp, otherWorkspace)
        TxnContext.doInTxn(readOnly = true) {
            val refs = deploy(first, sibling, otherApp, otherWorkspace)
            assertThat(refs[0]).containsExactly(EntityRef.valueOf("emodel/type@first"), EntityRef.valueOf("emodel/type@sibling"))
            assertThat(refs[1]).isSameAs(refs[0])
            assertThat(refs[2]).containsExactly(EntityRef.valueOf("emodel/type@other-app"))
            assertThat(refs[3]).containsExactly(EntityRef.valueOf("emodel/type@other-workspace"))
        }
        assertThat(inventoryReads).isEqualTo(3)
    }

    @Test
    fun bypassesCacheInWritableModeAndWithoutTransaction() {
        val first = artifact("first")
        inventory = listOf(first)
        deploy(first, first)
        TxnContext.doInTxn { deploy(first, first) }
        assertThat(inventoryReads).isEqualTo(4)
    }

    @Test
    fun doesNotReuseAcrossTransactions() {
        val first = artifact("first")
        val sibling = artifact("sibling")
        inventory = listOf(first)
        TxnContext.doInTxn(readOnly = true) { assertThat(deploy(first).single()).hasSize(1) }
        inventory = listOf(first, sibling)
        TxnContext.doInTxn(readOnly = true) { assertThat(deploy(first).single()).hasSize(2) }
        assertThat(inventoryReads).isEqualTo(2)
    }

    @Test
    fun skipsGlobalAndUnownedArtifacts() {
        val global = artifact("global", workspace = "")
        val unowned = artifact("unowned", app = "")
        inventory = listOf(global, unowned)
        TxnContext.doInTxn(readOnly = true) {
            deploy(global, unowned).forEach { assertThat(it).isEmpty() }
        }
        assertThat(inventoryReads).isZero()
    }
}
