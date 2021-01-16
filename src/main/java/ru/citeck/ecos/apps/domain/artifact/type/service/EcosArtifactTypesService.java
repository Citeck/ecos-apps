package ru.citeck.ecos.apps.domain.artifact.type.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.artifact.ArtifactMeta;
import ru.citeck.ecos.apps.artifact.ArtifactRef;
import ru.citeck.ecos.apps.artifact.ArtifactService;
import ru.citeck.ecos.apps.artifact.ArtifactService;
import ru.citeck.ecos.apps.artifact.type.ArtifactTypeService;
import ru.citeck.ecos.apps.artifact.type.ArtifactTypeService;
import ru.citeck.ecos.apps.artifact.type.TypeContext;
import ru.citeck.ecos.apps.domain.artifact.type.dto.EcosArtifactMeta;
import ru.citeck.ecos.apps.domain.artifact.type.repo.EcosArtifactTypesEntity;
import ru.citeck.ecos.apps.domain.content.service.EcosContentDao;
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity;
import ru.citeck.ecos.apps.domain.artifact.type.repo.EcosArtifactTypesRepo;
import ru.citeck.ecos.commons.io.file.EcosFile;
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir;
import ru.citeck.ecos.commons.utils.ZipUtils;
import ru.citeck.ecos.records2.RecordRef;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@DependsOn("liquibase")
@RequiredArgsConstructor
public class EcosArtifactTypesService {

    private static final EcosFile EMPTY_DIR = new EcosMemDir();

    private final EcosArtifactTypesRepo artifactTypesRepo;
    private final ArtifactTypeService artifactTypesService;
    private final ArtifactService artifactsService;
    private final EcosContentDao contentDao;

    private final Map<String, TypeContext> typeCtxByTypeId = new ConcurrentHashMap<>();
    private final Map<String, String> appNameByTypeId = new ConcurrentHashMap<>();
    private final Map<String, List<TypeContext>> typesByApp = new ConcurrentHashMap<>();
    private final Map<String, EcosFile> typesDirByApp = new ConcurrentHashMap<>();
    private final Map<AppSourceKey, String> typeBySource = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {

        artifactTypesRepo.getSources().forEach(source -> {

            try {
                log.info("Load types for " + source + " from DB");

                EcosArtifactTypesEntity types = artifactTypesRepo.findBySource(source);
                EcosFile typesDir = ZipUtils.extractZip(types.getContent().getData());

                List<TypeContext> typesCtx = artifactTypesService.loadTypes(typesDir);
                for (TypeContext ctx : typesCtx) {
                    registerType(source, ctx);
                }
                typesByApp.put(source, typesCtx);
                typesDirByApp.put(source, typesDir);

            } catch (Exception e) {
                log.error("Error with source: " + source, e);
            }
        });
    }

    public Instant getLastModified() {
        Instant lastModified = artifactTypesRepo.getLastModified();
        if (lastModified == null) {
            lastModified = Instant.EPOCH;
        }
        return lastModified;
    }

    public boolean isTypeRegistered(String type) {
        return getType(type) != null;
    }

    public TypeContext getType(String type) {
        return typeCtxByTypeId.get(type);
    }

    public List<String> getNonInternalTypes() {
        return typeCtxByTypeId.values()
            .stream()
            .filter(t -> !t.getMeta().getInternal())
            .map(TypeContext::getId)
            .collect(Collectors.toList());
    }

    public List<String> getTypesWithSourceId() {
        return typeCtxByTypeId.values()
            .stream()
            .filter(t -> StringUtils.isNotBlank(t.getMeta().getSourceId()))
            .map(TypeContext::getId)
            .collect(Collectors.toList());
    }

    public Set<String> getAllTypesIds() {
        return typeCtxByTypeId.keySet();
    }

    public List<TypeContext> getAllTypesCtx() {
        return new ArrayList<>(typeCtxByTypeId.values());
    }

    public String getAppNameByType(String type) {
        return appNameByTypeId.getOrDefault(type, "");
    }

    public EcosFile getTypesDirByApp(String appName) {
        return typesDirByApp.getOrDefault(appName, EMPTY_DIR);
    }

    public EcosFile getAllTypesDir() {
        EcosMemDir result = new EcosMemDir();
        typesDirByApp.values().forEach(result::copyFilesFrom);
        return result;
    }

    public void registerTypes(String appName, EcosFile typesDir, Instant lastModifiedByApp) {

        if (!typesDir.isDirectory()) {
            throw new IllegalArgumentException("Types dir should be a directory");
        }

        EcosArtifactTypesEntity repoTypes = artifactTypesRepo.findBySource(appName);

        if (repoTypes == null) {

            EcosContentEntity content = contentDao.upload(ZipUtils.writeZipAsBytes(typesDir));

            repoTypes = new EcosArtifactTypesEntity();
            repoTypes.setContent(content);
            repoTypes.setSource(appName);
            repoTypes.setLastModifiedByApp(lastModifiedByApp);

        } else if (lastModifiedByApp.isAfter(repoTypes.getLastModifiedByApp())) {

            EcosContentEntity content = contentDao.upload(ZipUtils.writeZipAsBytes(typesDir));

            repoTypes.setContent(content);
            repoTypes.setLastModifiedByApp(lastModifiedByApp);

        } else {

            return;
        }
        artifactTypesRepo.save(repoTypes);

        List<TypeContext> types = artifactTypesService.loadTypes(typesDir);
        typesByApp.put(appName, types);
        typesDirByApp.put(appName, typesDir);

        for (TypeContext ctx : types) {
            registerType(appName, ctx);
        }
    }

    private void registerType(String source, TypeContext ctx) {

        appNameByTypeId.put(ctx.getId(), source);
        typeBySource.put(new AppSourceKey(source, ctx.getMeta().getSourceId()), ctx.getId());
        typeCtxByTypeId.put(ctx.getId(), ctx);
    }

    public String getType(RecordRef recordRef) {
        return typeBySource.getOrDefault(new AppSourceKey(recordRef), "");
    }

    public List<TypeContext> getTypesByAppName(String appName) {
        return typesByApp.getOrDefault(appName, Collections.emptyList());
    }

    public String getAppByArtifactType(String moduleType) {
        String result = appNameByTypeId.getOrDefault(moduleType, "");
        return result != null ? result : "";
    }

    public EcosArtifactMeta getArtifactMeta(String typeId, Object artifact) {

        TypeContext type = artifactTypesService.getType(typeId);
        if (type == null) {
            throw new RuntimeException("Type is not found: " + typeId);
        }
        ArtifactMeta meta = artifactsService.getArtifactMeta(type, artifact);
        if (meta == null) {
            throw new RuntimeException("Artifact meta can't be received. Type: " + type + " Artifact: " + artifact);
        }

        ArtifactRef currentRef = ArtifactRef.create(typeId, meta.getId());

        List<ArtifactRef> dependencies = meta.getDependencies()
            .stream()
            .map(ref -> {
                String artifactType = getType(ref);
                if (StringUtils.isNotBlank(artifactType)) {
                    return ArtifactRef.create(artifactType, ref.getId());
                }
                return ArtifactRef.EMPTY;
            })
            .filter(ref -> !ref.equals(currentRef) && !ref.equals(ArtifactRef.EMPTY))
            .collect(Collectors.toList());

        return new EcosArtifactMeta(
            meta.getId(),
            meta.getName(),
            dependencies,
            meta.getTags()
        );
    }

    @Data
    @AllArgsConstructor
    private static class AppSourceKey {

        private String appName;
        private String sourceId;

        public AppSourceKey(RecordRef ref) {
            this.appName = ref.getAppName();
            this.sourceId = ref.getSourceId();
        }
    }

    @Data
    @AllArgsConstructor
    private static class TypeInfo {
        private TypeContext typeCtx;
        private Long changed;
    }
}
