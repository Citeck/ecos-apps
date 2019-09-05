package ru.citeck.ecos.apps.app.module.type.journal;

import ru.citeck.ecos.apps.app.module.type.ModuleFile;
import ru.citeck.ecos.apps.app.module.type.ModuleReader;

import java.util.Collections;
import java.util.List;

public class JournalsModuleReader implements ModuleReader<JournalModule> {

    @Override
    public List<JournalModule> read(ModuleFile file) {


        return null;
    }

    @Override
    public List<String> getModulePatterns() {
        return Collections.singletonList("journal/**/*.xml");
    }
}
