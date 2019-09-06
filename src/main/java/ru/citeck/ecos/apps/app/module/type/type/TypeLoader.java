package ru.citeck.ecos.apps.app.module.type.type;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.EcosModule;
import ru.citeck.ecos.apps.app.module.type.ModuleFile;
import ru.citeck.ecos.apps.app.module.type.ModuleReader;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Component
public class TypeLoader implements ModuleReader {

    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public List<EcosModule> read(String pattern, ModuleFile file) throws Exception {

        String value = file.read(in -> IOUtils.toString(in, StandardCharsets.UTF_8));
        TypeDto dto = mapper.readValue(value, TypeDto.class);

        EcosModule module = new TypeModule(value, dto.id, dto.name);

        return Collections.singletonList(module);
    }

    @Override
    public List<String> getFilePatterns() {
        return Collections.singletonList("types/**.json");
    }

    @Data
    public static class TypeDto {
        private String id;
        private String name;
    }
}
