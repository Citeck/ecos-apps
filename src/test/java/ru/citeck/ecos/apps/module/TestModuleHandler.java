package ru.citeck.ecos.apps.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.module.handler.EcosModuleHandler;
import ru.citeck.ecos.apps.module.handler.ModuleMeta;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;
import ru.citeck.ecos.commons.data.ObjectData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
public class TestModuleHandler implements EcosModuleHandler<ObjectData> {

    private Map<String, ObjectData> dataById = new ConcurrentHashMap<>();

    @Override
    public void deployModule(@NotNull ObjectData objectData) {
        dataById.put(objectData.get("id").asText(), objectData);
    }

    public ObjectData getById(String id) {
        return this.dataById.get(id);
    }

    @NotNull
    @Override
    public ModuleWithMeta<ObjectData> getModuleMeta(@NotNull ObjectData objectData) {
        return new ModuleWithMeta<>(objectData, new ModuleMeta(objectData.get("id").asText()));
    }

    @NotNull
    @Override
    public String getModuleType() {
        return "app/test";
    }

    @Override
    public void listenChanges(@NotNull Consumer<ObjectData> consumer) {
    }

    @Nullable
    @Override
    public ModuleWithMeta<ObjectData> prepareToDeploy(@NotNull ObjectData objectData) {
        return getModuleMeta(objectData);
    }
}
