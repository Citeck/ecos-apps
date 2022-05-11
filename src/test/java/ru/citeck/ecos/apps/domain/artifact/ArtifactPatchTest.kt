package ru.citeck.ecos.apps.domain.artifact

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.util.ResourceUtils
import ru.citeck.ecos.apps.EcosAppsApp
import ru.citeck.ecos.apps.domain.artifact.application.job.ApplicationsWatcherJob
import ru.citeck.ecos.apps.test.EcosTestApp
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.rabbitmq.RabbitMqConnProvider
import java.util.concurrent.ConcurrentHashMap

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [EcosAppsApp::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class ArtifactPatchTest {

    @Autowired
    private lateinit var connectionProvider: RabbitMqConnProvider
    @Autowired
    private lateinit var watcherJob: ApplicationsWatcherJob

    private val appByName = ConcurrentHashMap<String, EcosTestApp>()
    private val appByInstanceId = ConcurrentHashMap<String, EcosTestApp>()

    private val allApps = mutableListOf<EcosTestApp>()

    @BeforeEach
    fun before() {

        val connection = connectionProvider.getConnection()!!
        connection.waitUntilReady(2_000)

        listOf(
            "app0__0",
            "app1__0"
        ).map { readApp(it) }

        repeat(3) { watcherJob.forceUpdateSync() }
    }

    fun getAppByName(name: String): EcosTestApp {
        return appByName[name] ?: error("App with name $name is not found")
    }

    @Test
    fun test() {

        val testForm = getAppByName("app0")
            .getDeployedArtifacts("ui/form2", ObjectData::class.java)["test-form"]
                ?: error("test-form artifacts is not found")

        assertTrue(testForm.get("patch-0-applied").asBoolean())
        assertTrue(testForm.get("patch-1-applied").asBoolean())
        assertTrue(testForm.get("patch-2-applied").asBoolean())

        assertEquals("patched-by-patch-2", testForm.get("formKey").asText())
    }

    @AfterEach
    fun after() {
        allApps.forEach { it.dispose() }
    }

    private fun readApp(name: String): EcosTestApp {

        val app = EcosTestApp(
            ResourceUtils.getFile("src/test/resources/test/artifact-patch/apps/$name"),
            connectionProvider.getConnection()!!
        )

        appByName[app.getName()] = app
        appByInstanceId[app.getInstanceId()] = app
        allApps.add(app)

        return app
    }
}
