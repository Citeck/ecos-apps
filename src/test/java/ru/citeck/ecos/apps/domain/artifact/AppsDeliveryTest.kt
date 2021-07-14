package ru.citeck.ecos.apps.domain.artifact

import org.junit.*
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.util.ResourceUtils
import ru.citeck.ecos.apps.EcosAppsApp
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.apps.artifact.ArtifactService
import ru.citeck.ecos.apps.artifact.controller.type.binary.BinArtifact
import ru.citeck.ecos.apps.domain.artifact.application.job.ApplicationsWatcherJob
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.ArtifactRevSourceType
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.DeployStatus
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.test.EcosTestApp
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.rabbitmq.RabbitMqConnProvider
import java.util.concurrent.ConcurrentHashMap

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [EcosAppsApp::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class AppsDeliveryTest {

    @Autowired
    private lateinit var connectionProvider: RabbitMqConnProvider
    @Autowired
    private lateinit var watcherJob: ApplicationsWatcherJob
    @Autowired
    private lateinit var ecosArtifactsService: EcosArtifactsService
    @Autowired
    private lateinit var artifactsService: ArtifactService

    private val appByName = ConcurrentHashMap<String, EcosTestApp>()
    private val appByInstanceId = ConcurrentHashMap<String, EcosTestApp>()
    private val allApps = mutableListOf<EcosTestApp>()

    @Before
    fun before() {

        val connection = connectionProvider.getConnection()!!
        connection.waitUntilReady(2_000)

        listOf(
            "app0__0",
            "app1__0",
            "app2__0"
        ).map { readApp(it) }

        repeat(2) { watcherJob.forceUpdateSync() }
    }

    @Test
    fun test() {

        val app0Journals = appByName["app0"]!!.getDeployedArtifacts("ui/journal2", ObjectData::class.java)
        assertEquals(2, app0Journals.size)

        assertEquals("second", app0Journals["app2-journal0"]!!.get("/config/first").asText())
        val app2Imgs = appByName["app2"]!!.getDeployedArtifacts("ui/img", BinArtifact::class.java)

        assertEquals(2, app2Imgs.size)
        val app0ImgBytes = ResourceUtils.getFile(
            "src/test/resources/test/apps-delivery/" +
            "apps/app0__0/artifacts/ui/img/app0-image.png").readBytes()

        assertArrayEquals(app2Imgs["app0-image.png"]!!.data, app0ImgBytes)

        val formTypeId = "ui/form2"
        val firstFormRef = ArtifactRef.create(formTypeId, "first-form")
        val firstFormArtifact = ecosArtifactsService.getLastArtifact(firstFormRef)!!

        assertEquals(DeployStatus.DEPLOYED, firstFormArtifact.deployStatus)
        assertEquals(ArtifactRevSourceType.APPLICATION, firstFormArtifact.source.type)

        val newFirstForm = ObjectData.create("""
            {
                "id": "first-form",
                "user-prop": "user-value"
            }
        """.trimIndent())

        appByName["app1"]!!.addArtifactChangedByUser(formTypeId, newFirstForm)

        var firstFormArtifact2 = ecosArtifactsService.getLastArtifact(firstFormRef)!!
        var counter = 10
        while (counter-- > 0 && firstFormArtifact2.source.type == ArtifactRevSourceType.APPLICATION) {
            Thread.sleep(200)
            firstFormArtifact2 = ecosArtifactsService.getLastArtifact(firstFormRef)!!
        }
        assertEquals(DeployStatus.DEPLOYED, firstFormArtifact2.deployStatus)
        assertEquals(ArtifactRevSourceType.USER, firstFormArtifact2.source.type)

        val firstFormData = ecosArtifactsService.getArtifactData(firstFormRef)
        val firstFormDataFromDb = Json.mapper.convert(
            artifactsService.readArtifactFromBytes(formTypeId, firstFormData),
            ObjectData::class.java
        )!!
        assertEquals("user-value", firstFormDataFromDb.get("user-prop").asText())

        // patches test

        val secondOrigForm = appByName["app1"]!!.getDeployedArtifacts(formTypeId, ObjectData::class.java)["second-form"]!!
        assertEquals("abcdef", secondOrigForm.get("formKey").asText())

        readApp("app-with-patch__0")
        repeat(2) { watcherJob.forceUpdateSync() }

        val secondPatchedForm = appByName["app1"]!!.getDeployedArtifacts(formTypeId, ObjectData::class.java)["second-form"]!!
        assertEquals("alf_samwf:incomePackageTask_disabled_by_patch", secondPatchedForm.get("formKey").asText())
    }

    @After
    fun after() {
        allApps.forEach { it.dispose() }
    }

    private fun readApp(name: String): EcosTestApp {

        val app = EcosTestApp(
            ResourceUtils.getFile("src/test/resources/test/apps-delivery/apps/$name"),
            connectionProvider.getConnection()!!
        )

        appByName[app.getName()] = app
        appByInstanceId[app.getInstanceId()] = app
        allApps.add(app)

        return app
    }
}
