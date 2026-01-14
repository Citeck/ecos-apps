package ru.citeck.ecos.apps.domain.bundleinfo

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment

@Component
class CiteckBundleInfoRecordsDao(
    private val env: EcosWebAppEnvironment
) : RecordAttsDao {

    companion object {
        private const val PROP_KEY_PREFIX = "citeck.bundle"

        private val log = KotlinLogging.logger {}
    }

    private lateinit var bundleInfo: BundleInfo

    @PostConstruct
    fun init() {
        val bundleKey = env.getText("$PROP_KEY_PREFIX.key", "")
        if (bundleKey.isNotBlank()) {
            log.info { "Loaded bundle info with key $bundleKey" }
        } else {
            log.info { "Bundle info key is undefined" }
        }
        val txtContent = env.getText("$PROP_KEY_PREFIX.content", "")
        val content = if (txtContent.isNotBlank()) {
            Json.mapper.readDataNotNull(txtContent.trim())
        } else {
            DataValue.createObj()
        }
        bundleInfo = BundleInfo(bundleKey, content.asObjectData())
    }

    override fun getRecordAtts(recordId: String): Any? {
        if (recordId.isNotBlank()) {
            return null
        }
        return bundleInfo
    }

    override fun getId(): String {
        return "bundle-info"
    }

    class BundleInfo(
        val key: String,
        val content: ObjectData
    ) {

        fun getVersion(): String {
            return key.substringAfterLast('/')
        }
    }
}
