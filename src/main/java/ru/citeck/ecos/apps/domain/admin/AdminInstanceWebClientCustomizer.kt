package ru.citeck.ecos.apps.domain.admin

import de.codecentric.boot.admin.server.web.client.InstanceWebClient
import de.codecentric.boot.admin.server.web.client.InstanceWebClientCustomizer
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientRequest
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.webapp.lib.web.authenticator.Authentication
import ru.citeck.ecos.webapp.lib.web.authenticator.WebAuthenticatorsManager

@Component
class AdminInstanceWebClientCustomizer(
    val authenticatorManager: WebAuthenticatorsManager
) : InstanceWebClientCustomizer {

    override fun customize(instanceWebClientBuilder: InstanceWebClient.Builder) {
        val jwtAuth = authenticatorManager.getJwtAuthenticator("jwt")
        instanceWebClientBuilder.filter { _, request, next ->
            val runAs = AuthContext.getCurrentRunAsAuth()
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
