package ru.citeck.ecos.apps.app.module;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.module.type.ModuleTypesFactory;
import ru.citeck.ecos.apps.module.type.ModuleTypesRegistry;

@Component
public class EcosModuleTypesFactory extends ModuleTypesFactory {

    @Bean
    public synchronized ModuleTypesRegistry getTypesRegistry() {
        return super.getTypesRegistry();
    }
}
