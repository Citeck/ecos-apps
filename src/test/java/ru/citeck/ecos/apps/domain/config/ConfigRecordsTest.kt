package ru.citeck.ecos.apps.domain.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.apps.EcosAppsApp
import ru.citeck.ecos.apps.domain.config.dto.ConfigDef
import ru.citeck.ecos.apps.domain.config.eapps.ConfigArtifactHandler
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosAppsApp::class])
class ConfigRecordsTest {

    @Autowired
    lateinit var recordsService: RecordsService
    @Autowired
    lateinit var configArtifactHandler: ConfigArtifactHandler

    @Test
    fun configRepoTest() {

        val config = ConfigDef.create()
            .withId("test-conf")
            .withName(MLText("name"))
            .withScope("app/eapps")
            .withValue(DataValue.create("one two three"))
            .build()

        configArtifactHandler.deployArtifact(ObjectData.create(config))

        val result = recordsService.getAtt("config@app/eapps\$test-conf", "_value?raw").asJavaObj()
        assertThat(result).isEqualTo("one two three")
    }
}
