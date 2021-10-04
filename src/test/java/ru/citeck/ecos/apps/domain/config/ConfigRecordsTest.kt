package ru.citeck.ecos.apps.domain.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import ru.citeck.ecos.apps.EcosAppsApp
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.config.lib.artifact.dto.ConfigDef
import ru.citeck.ecos.records3.RecordsService

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [EcosAppsApp::class])
class ConfigRecordsTest {

    @Autowired
    lateinit var recordsService: RecordsService

    @Test
    fun configRepoTest() {

        val config = ConfigDef.create()
            .withName(MLText("name"))
            .withScope("app/eapps")
            .withValue(DataValue.create("one two three"))
            .build()

        val id = recordsService.mutate("eapps/config-repo@", config)

        val result = recordsService.getAtt(id, "value?raw").asJavaObj()
        assertThat(result).isEqualTo("one two three")
    }
}
