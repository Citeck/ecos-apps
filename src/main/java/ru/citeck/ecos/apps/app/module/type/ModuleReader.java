package ru.citeck.ecos.apps.app.module.type;

import ru.citeck.ecos.apps.app.module.EcosModule;

import java.util.List;

public interface ModuleReader<T extends EcosModule> {

    List<T> read(ModuleFile file);

    List<String> getModulePatterns();
}
