package ru.citeck.ecos.apps.app.module.type;

import ru.citeck.ecos.apps.app.module.EcosModule;

import java.util.List;

public interface ModuleReader {

    List<EcosModule> read(String pattern, ModuleFile file) throws Exception;

    List<String> getFilePatterns();
}
