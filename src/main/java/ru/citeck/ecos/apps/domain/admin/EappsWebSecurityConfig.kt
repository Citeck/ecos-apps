package ru.citeck.ecos.apps.domain.admin

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.webapp.lib.spring.context.webmvc.initEcosSecurity

@Order(90)
@Configuration
class EappsWebSecurityConfig {

    @Bean
    @Order(-200)
    fun eappsHttpSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http.initEcosSecurity().securityMatcher(
            AntPathRequestMatcher.antMatcher("/admin/**")
        ).authorizeHttpRequests {
            it.requestMatchers(AntPathRequestMatcher.antMatcher("/admin/**"))
                .hasAnyAuthority(AuthRole.ADMIN, AuthRole.SYSTEM)
        }.build()
    }
}
