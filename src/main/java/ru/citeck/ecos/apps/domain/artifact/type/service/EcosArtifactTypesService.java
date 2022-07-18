package ru.citeck.ecos.apps.domain.artifact.type.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.artifact.ArtifactMeta;
import ru.citeck.ecos.apps.artifact.ArtifactRef;
import ru.citeck.ecos.apps.artifact.ArtifactService;
import ru.citeck.ecos.apps.artifact.type.ArtifactTypeService;
import ru.citeck.ecos.apps.artifact.type.TypeContext;
import ru.citeck.ecos.apps.domain.artifact.type.dto.EcosArtifactMeta;
import ru.citeck.ecos.apps.domain.artifact.type.repo.EcosArtifactTypeEntity;
import ru.citeck.ecos.apps.domain.artifact.type.repo.EcosArtifactTypeRepo;
import ru.citeck.ecos.apps.domain.artifact.type.repo.EcosArtifactTypeRevEntity;
import ru.citeck.ecos.apps.domain.artifact.type.repo.EcosArtifactTypeRevRepo;
import ru.citeck.ecos.apps.domain.content.service.EcosContentDao;
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity;
import ru.citeck.ecos.commons.io.file.EcosFile;
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir;
import ru.citeck.ecos.commons.utils.ZipUtils;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

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

    private final EcosArtifactTypeRepo artifactTypeRepo;
    private final EcosArtifactTypeRevRepo artifactTypeRevRepo;

    private final ArtifactTypeService artifactTypeService;
    private final ArtifactService artifactsService;
    private final EcosContentDao contentDao;

    private final Map<String, Optional<EcosArtifactTypeContext>> typeCtxByTypeId = new ConcurrentHashMap<>();

    public Instant getLastModified() {
        Instant lastModified = artifactTypeRepo.getLastModified();
        if (lastModified == null) {
            lastModified = Instant.EPOCH;
        }
        return lastModified;
    }

    public boolean isTypeRegistered(String typeId) {
        if (typeCtxByTypeId.getOrDefault(typeId, Optional.empty()).isPresent()) {
            return true;
        }
        return artifactTypeRepo.findFirstByExtId(typeId) != null;
    }

    @Nullable
    public EcosArtifactTypeContext getTypeContext(String typeId) {

        return typeCtxByTypeId.computeIfAbsent(typeId, t -> {
            EcosArtifactTypeEntity typeEntity = artifactTypeRepo.findFirstByExtId(t);
            return Optional.ofNullable(mapTypeEntityToContext(typeEntity));
        }).orElse(null);
    }

    @Nullable
    private EcosArtifactTypeContext mapTypeEntityToContext(EcosArtifactTypeEntity entity) {

        if (entity == null || entity.getLastRev() == null) {
            return null;
        }
        EcosArtifactTypeRevEntity lastRev = entity.getLastRev();
        Long lastRevId = lastRev.getId();
        if (lastRevId == null) {
            return null;
        }
        try {
            TypeContext ctx = artifactTypeService.loadType(entity.getExtId(), lastRev.getContent().getData());
            return new EcosArtifactTypeContextImpl(lastRevId, ctx);
        } catch (Exception e) {
            log.error("Type reading failed. TypeId: " + entity.getExtId(), e);
            return null;
        }
    }

    public Set<String> getNonInternalTypes() {
        return artifactTypeRepo.findNonInternalTypeIds();
    }

    public Set<String> getTypesWithSourceId() {
        return artifactTypeRepo.findTypeIdsWithRecordsSourceId();
    }

    public Set<String> getAllTypesIds() {
        return artifactTypeRepo.getAllTypeIds();
    }

    public List<EcosArtifactTypeContext> getAllTypesCtx() {
        return getAllTypesIds()
            .stream()
            .map(this::getTypeContext)
            .collect(Collectors.toList());
    }

    public String getAppNameByType(String typeId) {
        EcosArtifactTypeEntity typeEntity = artifactTypeRepo.findFirstByExtId(typeId);
        return typeEntity != null ? typeEntity.getAppName() : "";
    }

    public EcosFile getAllTypesDir() {
        EcosMemDir result = new EcosMemDir();
        for (EcosArtifactTypeEntity entity : artifactTypeRepo.findAll()) {
            EcosArtifactTypeRevEntity lastRev = entity.getLastRev();
            if (lastRev == null) {
                continue;
            }
            EcosFile typeDir = ZipUtils.extractZip(lastRev.content.getData());
            result.createDir(entity.getExtId()).copyFilesFrom(typeDir);
        }
        return result;
    }

    public void registerTypes(String appName, EcosFile typesDir, Instant lastModifiedByApp) {

        if (!typesDir.isDirectory()) {
            throw new IllegalArgumentException("Types dir should be a directory");
        }

        List<TypeContext> typesCtx = artifactTypeService.readTypes(typesDir);
        for (TypeContext typeCtx : typesCtx) {

            EcosArtifactTypeEntity typeEntity = artifactTypeRepo.findFirstByExtId(typeCtx.getId());
            if (typeEntity != null && !lastModifiedByApp.isAfter(typeEntity.getLastModifiedByApp())) {
                continue;
            }

            byte[] typeZipContent = ZipUtils.writeZipAsBytes(typeCtx.getContent());
            EcosContentEntity typeContent = contentDao.upload(typeZipContent);

            EcosArtifactTypeRevEntity prevRev = null;

            if (typeEntity == null) {

                log.info("Create new type with id '" + typeCtx.getId() + "'");
                typeEntity = new EcosArtifactTypeEntity();
                typeEntity.setExtId(typeCtx.getId());

            } else {

                EcosArtifactTypeRevEntity lastRev = typeEntity.getLastRev();
                if (lastRev != null && Objects.equals(typeContent.getId(), lastRev.content.getId())) {
                    continue;
                }
                prevRev = lastRev;
            }

            log.info("Create new revision for type '" + typeCtx.getId() + "'");

            typeEntity.setInternal(typeCtx.getMeta().getInternal());
            typeEntity.setAppName(appName);
            typeEntity.setRecordsSourceId(typeCtx.getMeta().getSourceId());
            typeEntity.setLastModifiedByApp(lastModifiedByApp);
            typeEntity = artifactTypeRepo.save(typeEntity);

            EcosArtifactTypeRevEntity revEntity = new EcosArtifactTypeRevEntity();
            revEntity.setArtifactType(typeEntity);
            revEntity.setModelVersion(typeCtx.getMeta().getModelVersion().toString());
            revEntity.setContent(typeContent);
            revEntity.setPrevRev(prevRev);
            artifactTypeRevRepo.save(revEntity);

            typeEntity.setLastRev(revEntity);
            artifactTypeRepo.save(typeEntity);

            typeCtxByTypeId.remove(typeCtx.getId());
        }
    }

    public String getTypeIdForRecordRef(EntityRef recordRef) {
        if (EntityRef.isEmpty(recordRef)) {
            return "";
        }
        EcosArtifactTypeEntity typeEntity = artifactTypeRepo.findFirstByAppNameAndRecordsSourceId(
            recordRef.getAppName(),
            recordRef.getSourceId()
        );
        return typeEntity != null ? typeEntity.getExtId() : "";
    }

    public List<EcosArtifactTypeContext> getTypesByAppName(String appName) {

        List<EcosArtifactTypeContext> typeContexts = new ArrayList<>();

        for (EcosArtifactTypeEntity entity : artifactTypeRepo.findAllByAppName(appName)) {
            typeCtxByTypeId.computeIfAbsent(
                entity.getExtId(),
                typeId -> Optional.ofNullable(mapTypeEntityToContext(entity))
            ).ifPresent(typeContexts::add);
        }

        return typeContexts;
    }

    public EcosArtifactMeta getArtifactMeta(String typeId, Object artifact) {

        EcosArtifactTypeContext type = getTypeContext(typeId);
        if (type == null) {
            throw new RuntimeException("Type is not found: " + typeId);
        }
        ArtifactMeta meta = artifactsService.getArtifactMeta(type.getTypeContext(), artifact);
        if (meta == null) {
            throw new RuntimeException(
                "Artifact meta can't be received. " +
                "Type: " + type.getId() + " Artifact: " + artifact
            );
        }

        ArtifactRef currentRef = ArtifactRef.create(typeId, meta.getId());

        List<ArtifactRef> dependencies = meta.getDependencies()
            .stream()
            .map(ref -> {
                String artifactType = getTypeIdForRecordRef(ref);
                if (StringUtils.isNotBlank(artifactType)) {
                    return ArtifactRef.create(artifactType, ref.getLocalId());
                }
                return ArtifactRef.EMPTY;
            })
            .filter(ref -> !ref.equals(currentRef) && !ref.equals(ArtifactRef.EMPTY))
            .collect(Collectors.toList());

        return new EcosArtifactMeta(
            meta.getId(),
            meta.getName(),
            dependencies,
            meta.getTags(),
            type.getTypeRevId(),
            type.getMeta().getModelVersion(),
            meta.getSystem()
        );
    }
}
