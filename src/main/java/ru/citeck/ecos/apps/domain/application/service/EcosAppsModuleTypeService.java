package ru.citeck.ecos.apps.domain.application.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.apps.domain.content.service.EcosContentDao;
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity;
import ru.citeck.ecos.apps.domain.module.repo.EcosModuleTypesEntity;
import ru.citeck.ecos.apps.module.type.ModuleTypeService;
import ru.citeck.ecos.apps.module.type.TypeContext;
import ru.citeck.ecos.apps.domain.module.repo.EcosModuleTypesRepo;
import ru.citeck.ecos.commons.io.file.EcosFile;
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir;
import ru.citeck.ecos.commons.utils.ZipUtils;
import ru.citeck.ecos.records2.RecordRef;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@DependsOn("liquibase")
@RequiredArgsConstructor
public class EcosAppsModuleTypeService {

    private static final EcosFile EMPTY_DIR = new EcosMemDir();

    private final EcosModuleTypesRepo moduleTypesRepo;
    private final ModuleTypeService moduleTypeService;
    private final EcosContentDao contentDao;

    private final Map<String, TypeInfo> typeInfoByTypeId = new ConcurrentHashMap<>();
    private final Map<String, String> appNameByTypeId = new ConcurrentHashMap<>();
    private final Map<String, List<TypeContext>> typesByApp = new ConcurrentHashMap<>();
    private final Map<String, EcosFile> typesDirByApp = new ConcurrentHashMap<>();
    private final Map<AppSourceKey, String> typeBySource = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {

        moduleTypesRepo.getSources().forEach(source -> {

            try {
                log.info("Load types for " + source + " from DB");

                EcosModuleTypesEntity types = moduleTypesRepo.findFirstBySourceOrderByCreatedDateDesc(source);
                EcosFile typesDir = ZipUtils.extractZip(types.getContent().getData());

                List<TypeContext> typesCtx = moduleTypeService.getTypes(typesDir);
                for (TypeContext ctx : typesCtx) {
                    registerType(source, ctx, types.getCreatedDate());
                }
                typesByApp.put(source, typesCtx);
                typesDirByApp.put(source, typesDir);

            } catch (Exception e) {
                log.error("Error with source: " + source, e);
            }
        });
    }

    public boolean isTypeRegistered(String type) {
        return getType(type) != null;
    }

    public TypeContext getType(String type) {
        TypeInfo typeInfo = typeInfoByTypeId.get(type);
        return typeInfo != null ? typeInfo.getTypeCtx() : null;
    }

    public String getAppNameByType(String type) {
        return appNameByTypeId.getOrDefault(type, "");
    }

    public EcosFile getTypesDirByApp(String appName) {
        return typesDirByApp.getOrDefault(appName, EMPTY_DIR);
    }

    public void registerTypes(String appName, EcosFile typesDir) {

        if (!typesDir.isDirectory()) {
            throw new IllegalArgumentException("Types dir should be a directory");
        }

        EcosContentEntity content = contentDao.upload(ZipUtils.writeZipAsBytes(typesDir));
        EcosModuleTypesEntity repoTypes = moduleTypesRepo.findBySourceAndContent(appName, content);

        if (repoTypes == null) {
            repoTypes = new EcosModuleTypesEntity();
            repoTypes.setContent(content);
            repoTypes.setSource(appName);
            repoTypes = moduleTypesRepo.save(repoTypes);
        }

        List<TypeContext> types = moduleTypeService.getTypes(typesDir);
        typesByApp.put(appName, types);
        typesDirByApp.put(appName, typesDir);

        for (TypeContext ctx : types) {
            registerType(appName, ctx, repoTypes.getCreatedDate());
        }
    }

    private void registerType(String source, TypeContext ctx, Instant time) {

        TypeInfo typeInfo = typeInfoByTypeId.get(ctx.getId());
        long timeMs = time.toEpochMilli();

        if (typeInfo == null || typeInfo.changed < timeMs) {
            typeInfoByTypeId.put(ctx.getId(), new TypeInfo(ctx, timeMs));
            appNameByTypeId.put(ctx.getId(), source);
            typeBySource.put(new AppSourceKey(source, ctx.getSourceId()), ctx.getId());
        }
    }

    public String getType(RecordRef recordRef) {
        return typeBySource.getOrDefault(new AppSourceKey(recordRef), "");
    }

    public List<TypeContext> getTypesByAppName(String appName) {
        return typesByApp.getOrDefault(appName, Collections.emptyList());
    }

    public String getAppByModuleType(String moduleType) {
        String result = appNameByTypeId.getOrDefault(moduleType, "");
        return result != null ? result : "";
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
