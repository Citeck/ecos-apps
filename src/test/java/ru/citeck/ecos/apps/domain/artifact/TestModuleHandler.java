package ru.citeck.ecos.apps.domain.artifact;

import org.jetbrains.annotations.NotNull;
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler;
import ru.citeck.ecos.commons.data.ObjectData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

//@Component
public class TestModuleHandler implements EcosArtifactHandler<ObjectData> {

    private Map<String, ObjectData> dataById = new ConcurrentHashMap<>();

    @Override
    public void deployArtifact(@NotNull ObjectData objectData) {
        dataById.put(objectData.get("id").asText(), objectData);
    }

    public ObjectData getById(String id) {
        return this.dataById.get(id);
    }

    @NotNull
    @Override
    public String getArtifactType() {
        return "app/test";
    }

    @Override
    public void deleteArtifact(@NotNull String s) {
        dataById.remove(s);
    }

    @Override
    public void listenChanges(@NotNull Consumer<ObjectData> consumer) {
    }
}
