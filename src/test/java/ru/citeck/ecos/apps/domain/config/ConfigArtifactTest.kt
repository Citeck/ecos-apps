package ru.citeck.ecos.apps.domain.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import ru.citeck.ecos.apps.EcosAppsServiceFactory
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceProvider
import ru.citeck.ecos.apps.app.domain.artifact.source.DirectorySourceProvider
import ru.citeck.ecos.apps.artifact.ArtifactRef
import ru.citeck.ecos.commands.CommandsServiceFactory
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir
import ru.citeck.ecos.commons.io.file.std.EcosStdFile
import ru.citeck.ecos.commons.utils.TmplUtils
import ru.citeck.ecos.records3.RecordsServiceFactory
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProps
import java.io.File

class ConfigArtifactTest {

    @Test
    fun artifactRefTest() {

        val ref = "abc\$def\$hig"
        val artifactRef = ArtifactRef.valueOf(ref)

        assertThat(artifactRef.type).isEqualTo("abc")
        assertThat(artifactRef.id).isEqualTo("def\$hig")

        assertThat(ArtifactRef.valueOf(artifactRef.toString())).isEqualTo(artifactRef)
    }

    @Test
    fun templateTest() {

        val webAppContext = Mockito.mock(EcosWebAppApi::class.java)
        Mockito.`when`(webAppContext.getProperties())
            .thenReturn(EcosWebAppProps.create("test-app", "123456"))

        val recordsFactory = object : RecordsServiceFactory() {
            override fun getEcosWebAppApi(): EcosWebAppApi? {
                return webAppContext
            }
        }

        val assertTemplate = { template: String, value: Any?, expectedRes: String ->
            val templateAtts = TmplUtils.getAtts(template)
            val atts = recordsFactory.recordsService.getAtts(value, templateAtts)
            assertThat(TmplUtils.applyAtts(template, atts.getAtts()).asText()).isEqualTo(expectedRes)
        }

        val scopeTemplate = "\${scope!\$appName{?str|presuf('app/')}}"
        assertTemplate(scopeTemplate, DataValue.createObj(), "app/test-app")
        assertTemplate(scopeTemplate, DataValue.create("""{"scope":"value"}"""), "value")
    }

    @Test
    fun test() {

        val configWithoutScope = """
            ---
            id: config-without-scope
            value: some-value
        """.trimIndent()

        val configWithScope = """
            ---
            id: config-with-scope
            scope: some-scope
            value: some-value
        """.trimIndent()

        val artifactsDir = EcosMemDir()
        artifactsDir.createFile("app/config/config-with-scope.yml", configWithScope)
        artifactsDir.createFile("app/config/config-without-scope.yml", configWithoutScope)

        val webAppContext = Mockito.mock(EcosWebAppApi::class.java)
        Mockito.`when`(webAppContext.getProperties())
            .thenReturn(EcosWebAppProps.create("test-app", "123456"))

        val artifactsFactory = object : EcosAppsServiceFactory() {
            override fun createArtifactSourceProviders(): List<ArtifactSourceProvider> {
                return listOf(DirectorySourceProvider(artifactsDir))
            }
        }
        val recordsFactory = object : RecordsServiceFactory() {
            override fun getEcosWebAppApi(): EcosWebAppApi {
                return webAppContext
            }
        }
        artifactsFactory.recordsServices = recordsFactory
        artifactsFactory.commandsServices = CommandsServiceFactory()

        val typesDir = EcosStdFile(File("./src/main/resources/eapps/types"))
        artifactsFactory.artifactTypeService.loadTypes(typesDir)

        val type = artifactsFactory.artifactTypeService.getType("app/config")!!
        val configs = artifactsFactory.artifactService.readArtifacts(artifactsDir, type)
        val findConfig = { id: String ->
            configs.first {
                (it as ObjectData).get("id").asText() == id
            }
        }

        val metaWithoutScope = artifactsFactory.artifactService.getArtifactMeta(type, findConfig("config-without-scope"))
        val metaWithScope = artifactsFactory.artifactService.getArtifactMeta(type, findConfig("config-with-scope"))

        assertThat(metaWithoutScope!!.id).isEqualTo("app/test-app\$config-without-scope")
        assertThat(metaWithScope!!.id).isEqualTo("some-scope\$config-with-scope")
    }
}
