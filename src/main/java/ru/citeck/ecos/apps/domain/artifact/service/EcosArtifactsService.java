package ru.citeck.ecos.apps.domain.artifact.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.artifact.ArtifactRef;
import ru.citeck.ecos.apps.artifact.ArtifactsService;
import ru.citeck.ecos.apps.artifact.type.TypeContext;
import ru.citeck.ecos.apps.domain.artifact.service.deploy.ArtifactDeployer;
import ru.citeck.ecos.apps.domain.artifact.service.upload.ArtifactContext;
import ru.citeck.ecos.apps.domain.artifact.service.upload.ArtifactRevContext;
import ru.citeck.ecos.apps.domain.artifact.service.upload.policy.ArtifactSourcePolicy;
import ru.citeck.ecos.apps.domain.artifactpatch.service.ArtifactPatchService;
import ru.citeck.ecos.apps.domain.artifacttype.dto.EcosArtifactMeta;
import ru.citeck.ecos.apps.domain.artifacttype.service.EcosArtifactTypesService;
import ru.citeck.ecos.apps.domain.artifact.dto.*;
import ru.citeck.ecos.apps.domain.artifact.repo.*;
import ru.citeck.ecos.apps.domain.artifact.service.upload.ArtifactUploadDto;
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity;
import ru.citeck.ecos.apps.domain.content.service.EcosContentDao;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.records2.predicate.model.Predicate;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EcosArtifactsService {

    private final ArtifactsService artifactsService;

    private final EcosContentDao contentDao;
    private final EcosArtifactsRepo artifactsRepo;
    private final EcosArtifactsDao artifactsDao;
    private final EcosArtifactsDepRepo artifactsDepRepo;
    private final EcosArtifactsRevRepo artifactsRevRepo;
    private final EcosArtifactTypesService ecosArtifactTypesService;
    private final ArtifactPatchService artifactPatchService;

    private final List<ArtifactSourcePolicy> uploadPolicies;
    private Map<ArtifactSourceType, ArtifactSourcePolicy> uploadPolicyBySource;

    @PostConstruct
    public void init() {
        Map<ArtifactSourceType, ArtifactSourcePolicy> policiesMap = new HashMap<>();
        uploadPolicies.forEach(policy -> {
            policiesMap.put(policy.getSourceType(), policy);
        });
        uploadPolicyBySource = Collections.unmodifiableMap(policiesMap);
    }

    synchronized public void applyPatches(ArtifactRef artifactRef) {
        EcosArtifactEntity artifactEntity = getArtifactEntity(artifactRef);
        if (artifactEntity == null) {
            return;
        }
        applyPatches(artifactEntity);
    }

    public synchronized boolean removePatchedRev(ArtifactRef artifactRef) {
        EcosArtifactEntity artifactEntity = getArtifactEntity(artifactRef);
        if (artifactEntity == null) {
            return false;
        }
        if (removePatchedRev(artifactEntity)) {
            artifactsRepo.save(artifactEntity);
            return true;
        }
        return false;
    }

    @Nullable
    private EcosArtifactEntity getArtifactEntity(ArtifactRef artifactRef) {
        return artifactsRepo.getByExtId(artifactRef.getType(), artifactRef.getId());
    }

    private boolean removePatchedRev(EcosArtifactEntity artifactEntity) {

        if (artifactEntity.getPatchedRev() == null) {
            return false;
        }

        artifactEntity.setPatchedRev(null);

        if (artifactEntity.getLastRev() == null) {
            return true;
        }

        String typeId = artifactEntity.getType();

        byte[] artifactData = artifactEntity.getLastRev().getContent().getData();
        Object artifact = artifactsService.readArtifactFromBytes(typeId, artifactData);

        EcosArtifactMeta meta = ecosArtifactTypesService.getArtifactMeta(artifactEntity.getType(), artifact);
        extractArtifactMeta(artifactEntity, meta);

        artifactEntity.setDeployStatus(DeployStatus.DRAFT);

        return true;
    }

    private void applyPatches(EcosArtifactEntity artifactEntity) {

        EcosArtifactRevEntity lastRev = artifactEntity.getLastRev();
        if (lastRev == null) {
            removePatchedRev(artifactEntity);
            return;
        }

        EcosArtifactContext artifactContext = new EcosArtifactContext(artifactEntity);
        ArtifactSourcePolicy uploadPolicy = uploadPolicyBySource.get(lastRev.getSourceType());

        if (uploadPolicy == null || !uploadPolicy.isPatchingAllowed(artifactContext)) {
            removePatchedRev(artifactEntity);
            return;
        }

        String typeId = artifactEntity.getType();
        String artifactId = artifactEntity.getExtId();

        ArtifactRef artifactRef = ArtifactRef.create(typeId, artifactId);
        List<ArtifactPatchDto> patches = artifactPatchService.getPatches(artifactRef);

        if (patches == null || patches.isEmpty()) {
            removePatchedRev(artifactEntity);
            return;
        }

        Object artifact = artifactsService.readArtifactFromBytes(typeId, lastRev.getContent().getData());

        Object patchedArtifact = artifactPatchService.applyPatches(artifact, artifactRef, patches);
        byte[] patchedArtifactBytes = artifactsService.writeArtifactAsBytes(typeId, patchedArtifact);
        EcosContentEntity patchedContent = contentDao.upload(patchedArtifactBytes);

        if (patchedContent.getId().equals(lastRev.getContent().getId())) {
            // patches changed nothing with original revision
            artifactEntity.setPatchedRev(null);
            return;
        }

        EcosArtifactRevEntity currentPatchedRev = artifactEntity.getPatchedRev();

        if (currentPatchedRev != null && currentPatchedRev.getContent().getId().equals(patchedContent.getId())) {
            // patches changes are equals with current patched revision
            return;
        }

        EcosArtifactMeta patchedMeta = ecosArtifactTypesService.getArtifactMeta(typeId, patchedArtifact);

        if (patchedMeta == null) {
            log.error(
                "Patched artifact meta can't be received. " +
                    "Patches won't be applied. Patches: " + patches
            );
            removePatchedRev(artifactEntity);
            return;
        }

        extractArtifactMeta(artifactEntity, patchedMeta);

        EcosArtifactRevEntity lastPatchedRev = new EcosArtifactRevEntity();
        lastPatchedRev.setSourceId("");
        lastPatchedRev.setSourceType(ArtifactSourceType.PATCH);
        lastPatchedRev.setExtId(UUID.randomUUID().toString());
        lastPatchedRev.setContent(patchedContent);
        lastPatchedRev.setModule(artifactEntity);
        lastPatchedRev.setPrevRev(lastRev);

        lastPatchedRev = artifactsRevRepo.save(lastPatchedRev);

        artifactEntity.setPatchedRev(lastPatchedRev);
        artifactEntity.setDeployStatus(DeployStatus.DRAFT);
    }

    synchronized public boolean uploadArtifact(ArtifactUploadDto uploadDto) {

        ArtifactSourcePolicy uploadPolicy = uploadPolicyBySource.get(uploadDto.getSource().getType());
        if (uploadPolicy == null) {
            log.info("Artifact source is unknown: '" + uploadDto.getSource() + "'");
            return false;
        }

        String typeId = uploadDto.getType();
        Object artifact = uploadDto.getArtifact();

        TypeContext typeContext = artifactsService.getType(uploadDto.getType());

        if (typeContext == null) {
            log.error("Type '" + uploadDto.getType() + "' is not found. " +
                      "Artifact will be skipped: " + uploadDto.getArtifact());
            return false;
        }

        EcosArtifactMeta meta = ecosArtifactTypesService.getArtifactMeta(uploadDto.getType(), uploadDto.getArtifact());
        if (meta == null) {
            log.error("Artifact meta can't be evaluated. Type: '" + typeId + "' Artifact: " + artifact);
            return false;
        }

        if (StringUtils.isBlank(meta.getId())) {
            log.error("Artifact id is empty. Type: '" + typeId + "' Artifact: " + artifact);
            return false;
        }

        EcosArtifactEntity artifactEntity = artifactsRepo.getByExtId(typeId, meta.getId());

        if (artifactEntity == null) {

            artifactEntity = new EcosArtifactEntity();
            artifactEntity.setExtId(meta.getId());
            artifactEntity.setType(typeId);
            artifactEntity.setDeployStatus(DeployStatus.DRAFT);
            artifactEntity = artifactsRepo.save(artifactEntity);
        }

        EcosArtifactContext artifactContext = new EcosArtifactContext(artifactEntity);
        NewRevContext newRevContext = new NewRevContext(uploadDto);

        if (!uploadPolicy.isUploadAllowed(artifactContext, newRevContext)) {
            return false;
        }

        // update artifact

        boolean artifactChanged = false;
        if (ArtifactSourceType.ECOS_APP.equals(uploadDto.getSource().getType())
                && !artifactEntity.getEcosApp().equals(uploadDto.getSource().getId())) {

            artifactEntity.setEcosApp(uploadDto.getSource().getId());
            artifactChanged = true;
        }
        artifactChanged = extractArtifactMeta(artifactEntity, meta) || artifactChanged;

        if (artifactChanged) {
            artifactEntity = artifactsRepo.save(artifactEntity);
        }

        EcosContentEntity newContent = newRevContext.getContentEntity();
        if (artifactEntity.getLastRev() != null
             && artifactEntity.getLastRev().getContent().getId().equals(newContent.getId())) {

            // content doesn't changed
            return false;
        }

        if (log.isDebugEnabled()) {
            log.debug("Create new artifact revision entity "
                + meta.getName() + "(" + typeId + "$" + meta.getId() + ")");
        }

        EcosArtifactRevEntity lastRev = new EcosArtifactRevEntity();
        lastRev.setSourceId(uploadDto.getSource().getId());
        lastRev.setSourceType(uploadDto.getSource().getType());
        lastRev.setExtId(UUID.randomUUID().toString());
        lastRev.setContent(newContent);
        lastRev.setModule(artifactEntity);
        lastRev.setPrevRev(artifactEntity.getLastRev());

        lastRev = artifactsRevRepo.save(lastRev);

        artifactEntity.setLastRev(lastRev);

        applyPatches(artifactEntity);

        if (!ArtifactSourceType.USER.equals(uploadDto.getSource().getType())) {
            updateArtifactDeployStatus(artifactEntity);
        }

        artifactsRepo.save(artifactEntity);

        return true;
    }

    private boolean updateArtifactDeployStatus(EcosArtifactEntity artifact) {

        DeployStatus newStatus;

        if (artifact.getDependencies()
            .stream()
            .allMatch(dep -> DeployStatus.DEPLOYED.equals(dep.getTarget().getDeployStatus()))) {

            newStatus = DeployStatus.DRAFT;
        } else {
            newStatus = DeployStatus.DEPS_WAITING;
        }

        if (!newStatus.equals(artifact.getDeployStatus())) {
            artifact.setDeployStatus(newStatus);
            artifact.setDeployRetryCounter(0);
            return true;
        }
        return false;
    }

    private boolean extractArtifactMeta(EcosArtifactEntity artifactEntity, EcosArtifactMeta meta) {

        Set<EcosArtifactDepEntity> dependencies = getDependenciesArtifacts(artifactEntity, meta.getDependencies());
        boolean artifactWasChanged = artifactEntity.setDependencies(dependencies);

        MLText currentName = toNotNullMLText(artifactEntity.getName());
        if (!currentName.equals(meta.getName())) {
            artifactEntity.setName(Json.getMapper().toString(currentName));
            artifactWasChanged = true;
        }
        List<String> currentTags = DataValue.create(artifactEntity.getTags()).asStrList();
        if (!new ArrayList<>(currentTags).equals(new ArrayList<>(meta.getTags()))) {
            artifactEntity.setTags(Json.getMapper().toString(meta.getTags()));
            artifactWasChanged = true;
        }

        return artifactWasChanged;
    }

    private Set<EcosArtifactDepEntity> getDependenciesArtifacts(EcosArtifactEntity baseEntity,
                                                                Collection<ArtifactRef> dependencies) {

        Set<EcosArtifactDepEntity> dependencyEntities = new HashSet<>();

        Set<ArtifactRef> artifactsSet = new HashSet<>(dependencies);

        for (ArtifactRef ref : artifactsSet) {

            EcosArtifactEntity artifactEntity = artifactsRepo.getByExtId(ref.getType(), ref.getId());
            if (artifactEntity == null) {
                artifactEntity = new EcosArtifactEntity();
                artifactEntity.setExtId(ref.getId());
                artifactEntity.setType(ref.getType());
                artifactEntity.setDeployStatus(DeployStatus.CONTENT_WAITING);
                artifactEntity = artifactsRepo.save(artifactEntity);
            }

            EcosArtifactDepEntity depEntity = new EcosArtifactDepEntity();
            depEntity.setSource(baseEntity);
            depEntity.setTarget(artifactEntity);
            dependencyEntities.add(depEntity);
        }

        return new HashSet<>(dependencyEntities);
    }

    private MLText toNotNullMLText(String value) {
        if (StringUtils.isBlank(value)) {
            return new MLText();
        }
        return Json.getMapper().read(value, MLText.class);
    }

    synchronized public boolean deployArtifacts(ArtifactDeployer deployer) {

        if (deployer.getSupportedTypes().isEmpty()) {
            return false;
        }

        Map<Long, EcosArtifactEntity> artifactsToUpdateSourceDeps = new HashMap<>();

        int deployedCount = 0;

        for (String type : deployer.getSupportedTypes()) {

            List<EcosArtifactEntity> artifacts = artifactsRepo.findAllByTypeAndDeployStatus(type, DeployStatus.DRAFT);
            if (artifacts.isEmpty()) {
                continue;
            }

            log.info("Found " + artifacts.size() + " modules in DRAFT status with type " + type + " to deploy");

            int failedModulesCount = 0;

            for (EcosArtifactEntity entity : artifacts) {

                EcosArtifactRevEntity revToDeploy = entity.getPatchedRev();
                if (revToDeploy == null) {
                    revToDeploy = entity.getLastRev();
                }

                List<DeployError> errors = deployer.deploy(type, revToDeploy.getContent().getData());

                if (!errors.isEmpty()) {

                    log.error("Artifact deploy failed: " + type + "$" + entity.getExtId());
                    entity.setDeployStatus(DeployStatus.DEPLOY_FAILED);
                    Integer retryCounter = entity.getDeployRetryCounter();
                    entity.setDeployRetryCounter(retryCounter != null ? retryCounter + 1 : 1);
                    artifactsRepo.save(entity);
                    failedModulesCount++;

                } else {

                    entity.setDeployRetryCounter(0);
                    entity.setDeployStatus(DeployStatus.DEPLOYED);
                    entity = artifactsRepo.save(entity);

                    artifactsToUpdateSourceDeps.put(entity.getId(), entity);

                    deployedCount++;
                }
            }

            log.info("Deploy of type " + type + " is finished. " +
                "Success: " + (artifacts.size() - failedModulesCount) + " Failed: " + failedModulesCount);
        }

        Set<EcosArtifactEntity> entitiesToUpdate = artifactsToUpdateSourceDeps.values()
            .stream()
            .flatMap(entity -> artifactsDepRepo.getDepsByTarget(entity.getId())
                .stream()
                .map(EcosArtifactDepEntity::getSource))
            .filter(entity -> DeployStatus.DEPS_WAITING.equals(entity.getDeployStatus()))
            .collect(Collectors.toSet());

        for (EcosArtifactEntity entity : entitiesToUpdate) {
            if (updateArtifactDeployStatus(entity)) {
                log.info("Deploy status changed for " + entity.getExtId() + "$" + entity.getExtId()
                    + " to " + entity.getDeployStatus());
            }
        }

        return deployedCount > 0;
    }

    public List<EcosArtifact> getArtifactsByType(String type) {
        return artifactsRepo.findAllByType(type).stream()
            .map(EcosArtifactEntity::getLastRev)
            .map(this::toModule)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EcosArtifact> getAllArtifacts(int skipCount, int maxItems) {
        int page = skipCount / maxItems;
        return artifactsRepo.findAll(PageRequest.of(page, maxItems))
            .stream()
            .map(EcosArtifactEntity::getLastRev)
            .map(this::toModule)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getAllArtifactsCount(Predicate predicate) {
        return artifactsDao.getCount(predicate);
    }

    @Transactional(readOnly = true)
    public List<EcosArtifact> getAllArtifacts(Predicate predicate, int skipCount, int maxItems) {
        return artifactsDao.getAllLastRevisions(predicate, skipCount, maxItems)
            .stream()
            .map(this::toModule)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    @Nullable
    @Transactional(readOnly = true)
    public EcosArtifact getLastArtifact(ArtifactRef moduleRef) {
        return toModule(artifactsDao.getLastArtifactRev(moduleRef)).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<EcosArtifact> getAllArtifacts() {
        return getAllArtifacts(0, 1000);
    }

    synchronized public void setEcosAppFull(List<ArtifactRef> artifacts, String ecosAppId) {

        List<EcosArtifactEntity> currentArtifacts = artifactsRepo.findAllByEcosApp(ecosAppId);

        for (EcosArtifactEntity artifact : currentArtifacts) {
            ArtifactRef currentArtifactRef = ArtifactRef.create(artifact.getType(), artifact.getExtId());
            if (!artifacts.contains(currentArtifactRef)) {
                artifact.setEcosApp(null);
                artifactsRepo.save(artifact);
            }
        }

        for (ArtifactRef artifactRef : artifacts) {

            EcosArtifactEntity moduleEntity = artifactsRepo.getByExtId(artifactRef.getType(), artifactRef.getId());

            if (moduleEntity != null && StringUtils.isNotBlank(moduleEntity.getEcosApp())) {

                if (!moduleEntity.getEcosApp().equals(ecosAppId)) {
                    throw new IllegalArgumentException("Artifact " + artifactRef
                        + " already included in " + moduleEntity.getEcosApp() + " application");
                }
            }

            if (moduleEntity == null) {
                moduleEntity = new EcosArtifactEntity();
                moduleEntity.setExtId(artifactRef.getId());
                moduleEntity.setType(artifactRef.getType());
                moduleEntity = artifactsRepo.save(moduleEntity);
            }

            moduleEntity.setEcosApp(ecosAppId);
            artifactsRepo.save(moduleEntity);
        }
    }

    synchronized public void removeEcosApp(String ecosAppId) {
        artifactsDao.removeEcosApp(ecosAppId);
    }

    @Nullable
    public EcosArtifactRev getLastArtifactRev(ArtifactRef artifactRef) {
        EcosArtifactRevEntity lastModuleRev = artifactsDao.getLastArtifactRev(artifactRef);
        if (lastModuleRev == null) {
            return null;
        }
        return new EcosArtifactDb(lastModuleRev);
    }

    public List<ArtifactRef> getDependencies(ArtifactRef artifactRef) {

        EcosArtifactRevEntity lastArtifactRev = artifactsDao.getLastArtifactRev(artifactRef);

        return lastArtifactRev.getModule().getDependencies().stream()
            .map( dep -> ArtifactRef.create(dep.getTarget().getType(), dep.getTarget().getExtId()))
            .collect(Collectors.toList());
    }

    private Optional<EcosArtifact> toModule(EcosArtifactRevEntity entity) {

        if (entity == null) {
            return Optional.empty();
        }

        String type = entity.getModule().getType();
        byte[] content = entity.getContent().getData();

        if (ecosArtifactTypesService.isTypeRegistered(type)) {

            DeployStatus deployStatus = entity.getModule().getDeployStatus();
            if (deployStatus == null) {
                deployStatus = DeployStatus.CONTENT_WAITING;
            }
            return Optional.of(new EcosArtifact(
                entity.getModule().getExtId(),
                artifactsService.readArtifactFromBytes(type, content),
                type,
                Json.getMapper().read(entity.getModule().getName(), MLText.class),
                DataValue.create(entity.getModule().getTags()).asStrList(),
                deployStatus,
                new ArtifactsSourceInfo(
                    entity.getSourceId(),
                    entity.getSourceType()
                ),
                entity.getExtId()
            ));
        }
        return Optional.empty();
    }

    @RequiredArgsConstructor
    private class EcosArtifactContext implements ArtifactContext {

        final EcosArtifactEntity artifactEntity;

        @NotNull
        @Override
        public String getEcosApp() {
            String app = artifactEntity.getEcosApp();
            if (StringUtils.isBlank(app)) {
                return "";
            }
            return app;
        }

        @Nullable
        @Override
        public ArtifactRevContext getLastRevBySourceType(@NotNull ArtifactSourceType type) {
            List<EcosArtifactRevEntity> revs = artifactsRevRepo.getArtifactRevisions(
                artifactEntity.getType(),
                artifactEntity.getExtId(),
                type,
                PageRequest.of(0, 1)
            );
            return revs.isEmpty() ? null : new RevContext(revs.get(0));
        }

        @Nullable
        @Override
        public ArtifactRevContext getLastRev() {
            EcosArtifactRevEntity lastRev = artifactEntity.getLastRev();
            return lastRev == null ? null : new RevContext(lastRev);
        }
    }

    @RequiredArgsConstructor
    private class NewRevContext implements ArtifactRevContext {

        final ArtifactUploadDto uploadDto;
        private EcosContentEntity contentEntity;

        @Override
        public long getContentId() {
            return getContentEntity().getId();
        }

        @NotNull
        @Override
        public ArtifactSourceType getSourceType() {
            return uploadDto.getSource().getType();
        }

        @NotNull
        @Override
        public String getSourceId() {
            return uploadDto.getSource().getId();
        }

        public EcosContentEntity getContentEntity() {
            if (contentEntity == null) {
                byte[] artifactBytes = artifactsService.writeArtifactAsBytes(
                    uploadDto.getType(),
                    uploadDto.getArtifact()
                );
                contentEntity = contentDao.upload(artifactBytes);
            }
            return contentEntity;
        }
    }

    @RequiredArgsConstructor
    private static class RevContext implements ArtifactRevContext {

        final EcosArtifactRevEntity rev;

        @Override
        public long getContentId() {
            return rev.getContent().getId();
        }

        @NotNull
        @Override
        public ArtifactSourceType getSourceType() {
            return rev.getSourceType();
        }

        @NotNull
        @Override
        public String getSourceId() {
            return rev.getSourceId();
        }
    }
}
