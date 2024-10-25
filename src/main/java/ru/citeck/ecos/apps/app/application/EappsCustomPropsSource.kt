package ru.citeck.ecos.apps.app.application

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import java.net.URI

class EappsCustomPropsSource : EnvironmentPostProcessor {

    companion object {
        const val PROPS_NAME = "eapps-custom-properties"

        private const val ADMIN_PUBLIC_URL_KEY = "spring.boot.admin.ui.public-url"
        private const val WEB_URL_KEY = "ecos.webapp.properties.webUrl"
        private const val ADMIN_CONTEXT_PATH_KEY = "spring.boot.admin.context-path"

        private const val WEB_APP_CONTEXT_PATH = "/gateway/eapps"

        private const val DEFAULT_WEB_URL = "http://localhost"
    }

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {

        val webUrl = environment.getProperty(WEB_URL_KEY) ?: ""
        var adminPublicUrl = webUrl.ifEmpty { DEFAULT_WEB_URL }
        val uri = URI(adminPublicUrl)
        if (uri.port == -1) {
            adminPublicUrl += if (adminPublicUrl.startsWith("https://")) {
                ":443"
            } else {
                ":80"
            }
        }
        adminPublicUrl += WEB_APP_CONTEXT_PATH + environment.getProperty(ADMIN_CONTEXT_PATH_KEY)
        val eappsProps = MapPropertySource(PROPS_NAME, mapOf(
            ADMIN_PUBLIC_URL_KEY to adminPublicUrl
        ))
        environment.propertySources.addLast(eappsProps)
    }
}
