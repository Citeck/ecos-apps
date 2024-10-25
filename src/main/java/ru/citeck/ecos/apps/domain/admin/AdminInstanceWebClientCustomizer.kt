package ru.citeck.ecos.apps.domain.admin

import de.codecentric.boot.admin.server.web.client.InstanceWebClient
import de.codecentric.boot.admin.server.web.client.InstanceWebClientCustomizer
import org.springframework.http.HttpMethod
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import ru.citeck.ecos.commons.x509.EcosX509Registry
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.webapp.lib.spring.context.web.NettyHttpClientUtils
import ru.citeck.ecos.webapp.lib.web.authenticator.Authentication
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticatorsManager
import ru.citeck.ecos.webapp.lib.web.webapi.client.props.EcosWebClientProps

@Component
class AdminInstanceWebClientCustomizer(
    val authenticatorManager: WebAuthenticatorsManager,
    private val x509Registry: EcosX509Registry,
    private val webClientProps: EcosWebClientProps,
    private val webClientBuilder: WebClient.Builder
) : InstanceWebClientCustomizer {

    override fun customize(instanceWebClientBuilder: InstanceWebClient.Builder) {

        val httpClient =  NettyHttpClientUtils.configureTls(HttpClient.create(), x509Registry, webClientProps.tls)
            .httpResponseDecoder { it.maxHeaderSize(40_000) }
        val webClient = webClientBuilder.clone().clientConnector(ReactorClientHttpConnector(httpClient))
        instanceWebClientBuilder.webClient(webClient)

        val jwtAuth = authenticatorManager.getJwtAuthenticator("jwt")
        instanceWebClientBuilder.filter { _, request, next ->
            var runAs = AuthContext.getCurrentRunAsAuth()
            if (runAs.isEmpty() && (request.method() == HttpMethod.GET || request.method() == HttpMethod.OPTIONS)) {
                runAs = AuthContext.SYSTEM_AUTH
            }
            val newRequest = if (runAs.isEmpty()) {
                request
            } else {
                ClientRequest.from(request)
                    .header(
                        "Authorization", jwtAuth.getAuthHeader(
                            Authentication(AuthContext.getCurrentUser(), runAs)
                        )
                    )
                    .build()
            }
            next.exchange(newRequest)
        }
    }
}
