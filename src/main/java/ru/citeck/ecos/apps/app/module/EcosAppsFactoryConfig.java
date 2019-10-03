package ru.citeck.ecos.apps.app.module;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.apps.EcosAppsFactory;
import ru.citeck.ecos.apps.module.type.ModuleTypesRegistry;

@Configuration
public class EcosAppsFactoryConfig extends EcosAppsFactory {

    @Bean
    @Override
    protected ModuleTypesRegistry createModuleTypesRegistry() {
        return super.createModuleTypesRegistry();
    }
}
