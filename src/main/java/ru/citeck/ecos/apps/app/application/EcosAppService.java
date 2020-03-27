package ru.citeck.ecos.apps.app.application;

import kotlin.Unit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.app.EcosApp;
import ru.citeck.ecos.apps.app.provider.ComputedMeta;
import ru.citeck.ecos.apps.domain.AppModuleTypeMetaEntity;
import ru.citeck.ecos.apps.domain.EcosAppEntity;
import ru.citeck.ecos.apps.module.type.TypeContext;
import ru.citeck.ecos.apps.repository.AppModuleTypeMetaRepo;
import ru.citeck.ecos.apps.repository.EcosAppRepo;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EcosAppService {

    private final EcosAppRepo appRepo;
    private final AppModuleTypeMetaRepo typesRepo;

    public Optional<EcosApp> getApp(String appId) {
        return Optional.ofNullable(mapToDto(appRepo.getByExtId(appId)));
    }

    public void saveApp(EcosApp app) {
        appRepo.save(mapToEntity(app));
    }

    public Map<String, AppModuleTypeMeta> getAppModuleTypesMeta(String appId, List<TypeContext> types) {

        EcosAppEntity appEntity = appRepo.getByExtId(appId);
        if (appEntity == null || types == null || types.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<String> typesId = types.stream().map(TypeContext::getId).collect(Collectors.toSet());
        List<AppModuleTypeMetaEntity> meta = typesRepo.findAllByAppAndModuleTypeIn(appEntity, typesId);

        Map<String, AppModuleTypeMeta> result = new HashMap<>();
        meta.forEach(m -> {
            AppModuleTypeMeta typeMeta = new AppModuleTypeMeta();
            typeMeta.setLastConsumedMs(m.getLastConsumed());
            result.put(m.getModuleType(), typeMeta);
        });

        return result;
    }

    public boolean updateAppModuleTypesMeta(String appName, Map<String, List<ComputedMeta>> metaByType) {

        if (metaByType == null) {
            return false;
        }

        AtomicBoolean changed = new AtomicBoolean();

        metaByType.forEach((type, modules) -> {

            long lastConsumed = 0L;
            for (ComputedMeta meta : modules) {
                lastConsumed = Math.max(meta.getModifiedMs(), lastConsumed);
            }

            EcosAppEntity appEntity = appRepo.getByExtId(appName);
            if (appEntity == null) {
                appEntity = new EcosAppEntity();
                appEntity.setExtId(appName);
                appEntity.setVersion("1.0.0");
                appEntity = appRepo.save(appEntity);
            }

            AppModuleTypeMetaEntity metaEntity = typesRepo.findByAppAndModuleType(appEntity, type).orElse(null);
            if (metaEntity == null) {
                metaEntity = new AppModuleTypeMetaEntity();
                metaEntity.setApp(appEntity);
                metaEntity.setModuleType(type);
            }

            if (metaEntity.getLastConsumed() < lastConsumed) {
                metaEntity.setLastConsumed(lastConsumed);
                typesRepo.save(metaEntity);
                changed.set(true);
            }
        });

        return changed.get();
    }

    private EcosAppEntity mapToEntity(EcosApp app) {

        EcosAppEntity entity = appRepo.getByExtId(app.getId());
        if (entity == null) {
            entity = new EcosAppEntity();
            entity.setExtId(app.getId());
        }

        entity.setVersion(app.getVersion().toString());
        entity.setIsSystem(app.isSystem());
        entity.setType(app.getType());

        return entity;
    }

    private EcosApp mapToDto(EcosAppEntity entity) {
        if (entity == null) {
            return null;
        }
        return EcosApp.create(b -> {
            b.setId(entity.getExtId());
            b.setType(entity.getType());
            b.setVersion(entity.getVersion());
            return Unit.INSTANCE;
        });
    }
}
