package ru.citeck.ecos.apps.domain.artifact.artifact.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType;
import ru.citeck.ecos.apps.app.domain.artifact.source.SourceKey;
import ru.citeck.ecos.apps.artifact.ArtifactRef;
import ru.citeck.ecos.apps.artifact.ArtifactService;
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.*;
import ru.citeck.ecos.apps.domain.artifact.artifact.repo.*;
import ru.citeck.ecos.apps.domain.artifact.artifact.service.deploy.ArtifactDeployer;
import ru.citeck.ecos.apps.domain.artifact.artifact.service.upload.ArtifactContext;
import ru.citeck.ecos.apps.domain.artifact.artifact.service.upload.ArtifactRevContext;
import ru.citeck.ecos.apps.domain.artifact.artifact.service.upload.policy.ArtifactSourcePolicy;
import ru.citeck.ecos.apps.domain.artifact.patch.dto.ArtifactPatchDto;
import ru.citeck.ecos.apps.domain.artifact.type.dto.EcosArtifactMeta;
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypeContext;
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService;
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity;
import ru.citeck.ecos.apps.domain.content.service.EcosContentDao;
import ru.citeck.ecos.apps.eapps.dto.ArtifactUploadDto;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.records2.predicate.model.Predicate;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EcosArtifactsService {

    private static final Duration[] DEPLOY_RETRY_TIME = {
        Duration.ofSeconds(10),
        Duration.ofMinutes(1),
        Duration.ofMinutes(10),
        Duration.ofMinutes(60),
        Duration.ofHours(12),
        Duration.ofDays(2)
    };

    private final ArtifactService artifactsService;

    private final EcosContentDao contentDao;
    private final EcosArtifactsRepo artifactsRepo;
    private final EcosArtifactsDao artifactsDao;
    private final EcosArtifactsDepRepo artifactsDepRepo;
    private final EcosArtifactsRevRepo artifactsRevRepo;
    private final EcosArtifactTypesService ecosArtifactTypesService;
    private final EcosArtifactsPatchService artifactPatchService;

    private final List<ArtifactSourcePolicy> uploadPolicies;
    private Map<ArtifactSourceType, ArtifactSourcePolicy> uploadPolicyBySource;

    @PostConstruct
    public void init() {
        Map<ArtifactSourceType, ArtifactSourcePolicy> policiesMap = new HashMap<>();
        uploadPolicies.forEach(policy ->
            policiesMap.put(policy.getSourceType(), policy)
        );
        uploadPolicyBySource = Collections.unmodifiableMap(policiesMap);
    }

    @Transactional(readOnly = true)
    public Instant getLastModifiedTime() {
        Instant lastModified = artifactsRepo.getLastModifiedTime();
        if (lastModified == null) {
            lastModified = Instant.EPOCH;
        }
        return lastModified;
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
        ArtifactSourcePolicy uploadPolicy = uploadPolicyBySource.get(
            ArtifactSourceType.valueOf(lastRev.getSourceType().toString())
        );

        if (uploadPolicy == null || !uploadPolicy.isPatchingAllowed(artifactContext)) {
            removePatchedRev(artifactEntity);
            return;
        }

        String typeId = artifactEntity.getType();
        String artifactId = artifactEntity.getExtId();

        ArtifactRef artifactRef = ArtifactRef.create(typeId, artifactId);
        List<ArtifactPatchDto> patches = artifactPatchService.getPatches(artifactRef);

        if (patches.isEmpty()) {
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
        lastPatchedRev.setSourceType(ArtifactRevSourceType.PATCH);
        lastPatchedRev.setExtId(UUID.randomUUID().toString());
        lastPatchedRev.setContent(patchedContent);
        lastPatchedRev.setArtifact(artifactEntity);
        lastPatchedRev.setPrevRev(lastRev);
        lastPatchedRev.setTypeRevId(patchedMeta.getTypeRevId());
        lastPatchedRev.setModelVersion(patchedMeta.getModelVersion().toString());

        lastPatchedRev = artifactsRevRepo.save(lastPatchedRev);

        artifactEntity.setPatchedRev(lastPatchedRev);
        artifactEntity.setDeployStatus(DeployStatus.DRAFT);
    }

    synchronized public boolean uploadArtifact(ArtifactUploadDto uploadDto) {

        SourceKey sourceKey = uploadDto.getSource().getSource();

        ArtifactSourcePolicy uploadPolicy = uploadPolicyBySource.get(sourceKey.getType());
        if (uploadPolicy == null) {
            log.info("Artifact source is unknown: '" + uploadDto.getSource() + "'");
            return false;
        }

        ArtifactRevSourceType revSourceType = ArtifactRevSourceType.valueOf(sourceKey.getType().name());

        String typeId = uploadDto.getType();
        Object artifact = uploadDto.getArtifact();

        EcosArtifactTypeContext typeContext = ecosArtifactTypesService.getTypeContext(uploadDto.getType());

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
            if (DeployStatus.DEPLOY_FAILED.equals(artifactEntity.getDeployStatus())) {
                artifactEntity.setDeployRetryCounter(1);
                artifactsRepo.save(artifactEntity);
            }
            return false;
        }

        // update artifact

        boolean artifactChanged = false;
        if (ArtifactRevSourceType.ECOS_APP.equals(revSourceType)
                && !Objects.equals(artifactEntity.getEcosApp(), sourceKey.getId())) {

            artifactEntity.setEcosApp(sourceKey.getId());
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
                + (meta.getName() == null ? "{}" : meta.getName())
                + "(" + typeId + "$" + meta.getId() + ")");
        }

        EcosArtifactRevEntity lastRev = new EcosArtifactRevEntity();
        lastRev.setSourceId(sourceKey.getId());
        lastRev.setSourceType(revSourceType);
        lastRev.setExtId(UUID.randomUUID().toString());
        lastRev.setContent(newContent);
        lastRev.setArtifact(artifactEntity);
        lastRev.setPrevRev(artifactEntity.getLastRev());
        lastRev.setTypeRevId(typeContext.getTypeRevId());
        lastRev.setModelVersion(meta.getModelVersion().toString());

        lastRev = artifactsRevRepo.save(lastRev);

        artifactEntity.setLastRev(lastRev);

        applyPatches(artifactEntity);

        if (!ArtifactRevSourceType.USER.equals(revSourceType)) {
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

            DeployStatus deployStatusBefore = artifact.getDeployStatus();

            artifact.setDeployStatus(newStatus);
            artifact.setDeployRetryCounter(0);
            artifact.setDeployErrors(null);
            artifact.setDeployMsg(null);

            printDeployStatusChanged(deployStatusBefore, artifact);

            return true;
        }
        return false;
    }

    private boolean extractArtifactMeta(EcosArtifactEntity artifactEntity, EcosArtifactMeta meta) {

        Set<EcosArtifactDepEntity> dependencies = getDepsEntities(artifactEntity, meta.getDependencies());
        boolean artifactWasChanged = artifactEntity.setDependencies(dependencies);

        MLText currentName = toNotNullMLText(artifactEntity.getName());
        if (!currentName.equals(meta.getName())) {
            artifactEntity.setName(Json.getMapper().toString(meta.getName()));
            artifactWasChanged = true;
        }
        List<String> currentTags = DataValue.create(artifactEntity.getTags()).asStrList();
        if (!new ArrayList<>(currentTags).equals(new ArrayList<>(meta.getTags()))) {
            artifactEntity.setTags(Json.getMapper().toString(meta.getTags()));
            artifactWasChanged = true;
        }

        if (!Objects.equals(artifactEntity.getTypeRevId(), meta.getTypeRevId())) {
            artifactEntity.setTypeRevId(meta.getTypeRevId());
            artifactWasChanged = true;
        }

        if (!Objects.equals(artifactEntity.getSystem(), meta.getSystem())) {
            artifactEntity.setSystem(meta.getSystem());
            artifactWasChanged = true;
        }

        return artifactWasChanged;
    }

    private Set<EcosArtifactDepEntity> getDepsEntities(EcosArtifactEntity baseEntity,
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

    synchronized public void updateFailedArtifacts() {

        Instant now = Instant.now();

        for (int retryCounter = 0; retryCounter < DEPLOY_RETRY_TIME.length; retryCounter++) {

            Instant changedBefore = now.minusMillis(DEPLOY_RETRY_TIME[retryCounter].toMillis());

            List<EcosArtifactEntity> failedArtifacts = artifactsRepo.findArtifactsToRetry(
                DeployStatus.DEPLOY_FAILED,
                retryCounter + 1,
                changedBefore
            );

            failedArtifacts.forEach(artifact -> {

                DeployStatus deployStatusBefore = artifact.getDeployStatus();
                artifact.setDeployStatus(DeployStatus.DRAFT);
                artifactsDao.save(artifact);

                printDeployStatusChanged(deployStatusBefore, artifact);
            });
        }
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
                DeployStatus deployStatusBefore = entity.getDeployStatus();

                if (!errors.isEmpty()) {

                    log.error("Artifact deploy failed: " + type + "$" + entity.getExtId() + ". Errors: \n" +
                        errors.stream()
                            .map(it -> Json.getMapper()
                            .toString(it))
                            .collect(Collectors.joining("\n"))
                    );

                    entity.setDeployStatus(DeployStatus.DEPLOY_FAILED);
                    Integer retryCounter = entity.getDeployRetryCounter();
                    entity.setDeployRetryCounter(retryCounter != null ? retryCounter + 1 : 1);

                    String errorsMsg = errors.stream()
                        .map(DeployError::getMessage)
                        .collect(Collectors.joining("\n"));

                    entity.setDeployMsg(errorsMsg);
                    entity.setDeployErrors(Json.getMapper().toString(errors));

                    artifactsRepo.save(entity);

                    failedModulesCount++;

                } else {

                    entity.setDeployRetryCounter(0);
                    entity.setDeployStatus(DeployStatus.DEPLOYED);
                    entity.setDeployMsg(null);
                    entity.setDeployErrors(null);

                    entity = artifactsRepo.save(entity);

                    artifactsToUpdateSourceDeps.put(entity.getId(), entity);

                    deployedCount++;
                }

                printDeployStatusChanged(deployStatusBefore, entity);
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
            updateArtifactDeployStatus(entity);
        }

        return deployedCount > 0;
    }

    private void printDeployStatusChanged(DeployStatus before, EcosArtifactEntity entity) {
        if (!Objects.equals(before, entity.getDeployStatus())) {
            log.info("Deploy status changed for " + entity.getType() + "$" + entity.getExtId()
                + " from " + before
                + " to " + entity.getDeployStatus());
        }
    }

    @Transactional(readOnly = true)
    public List<EcosArtifactDto> getArtifactsByType(String type) {
        return artifactsRepo.findAllByType(type).stream()
            .map(EcosArtifactEntity::getLastRev)
            .map(this::toModule)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EcosArtifactDto> getAllArtifacts(int skipCount, int maxItems) {
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
    public List<EcosArtifactDto> getAllArtifacts(Predicate predicate, int skipCount, int maxItems) {
        return artifactsDao.getAllLastRevisions(predicate, skipCount, maxItems)
            .stream()
            .map(this::toModule)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    @Nullable
    @Transactional(readOnly = true)
    public EcosArtifactDto getLastArtifact(ArtifactRef moduleRef) {
        return toModule(artifactsDao.getLastArtifactRev(moduleRef)).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<EcosArtifactDto> getAllArtifacts() {
        return getAllArtifacts(0, 1000);
    }

    public void resetDeployStatus(ArtifactRef artifactRef) {
        EcosArtifactEntity artifact = artifactsDao.getArtifact(artifactRef);
        if (artifact != null) {
            artifact.setDeployStatus(DeployStatus.DRAFT);
            artifact.setDeployErrors(null);
            artifact.setDeployMsg(null);
            artifact.setDeployRetryCounter(0);
            artifactsRepo.save(artifact);
        }
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
    @Transactional(readOnly = true)
    public EcosArtifactRev getLastArtifactRev(ArtifactRef artifactRef) {
        EcosArtifactRevEntity lastModuleRev = artifactsDao.getLastArtifactRev(artifactRef);
        if (lastModuleRev == null) {
            return null;
        }
        return new EcosArtifactDb(lastModuleRev);
    }

    @Transactional(readOnly = true)
    public List<ArtifactRef> getDependencies(ArtifactRef artifactRef) {

        EcosArtifactRevEntity lastArtifactRev = artifactsDao.getLastArtifactRev(artifactRef);

        return lastArtifactRev.getArtifact().getDependencies().stream()
            .map( dep -> ArtifactRef.create(dep.getTarget().getType(), dep.getTarget().getExtId()))
            .collect(Collectors.toList());
    }

    private Optional<EcosArtifactDto> toModule(EcosArtifactRevEntity entity) {

        if (entity == null) {
            return Optional.empty();
        }

        String type = entity.getArtifact().getType();
        byte[] content = entity.getContent().getData();

        DeployStatus deployStatus = entity.getArtifact().getDeployStatus();
        if (deployStatus == null) {
            deployStatus = DeployStatus.CONTENT_WAITING;
        }

        ArtifactRevSourceType sourceType = entity.getSourceType();
        if (sourceType == null) {
            sourceType = ArtifactRevSourceType.APPLICATION;
        }
        String sourceId = entity.getSourceId();
        if (sourceId == null) {
            sourceId = "";
        }

        return Optional.of(new EcosArtifactDto(
            entity.getArtifact().getExtId(),
            artifactsService.readArtifactFromBytes(type, content),
            type,
            Json.getMapper().read(entity.getArtifact().getName(), MLText.class),
            DataValue.create(entity.getArtifact().getTags()).asStrList(),
            deployStatus,
            new ArtifactRevSourceInfo(sourceId, sourceType),
            Boolean.TRUE.equals(entity.getArtifact().getSystem()),
            entity.getExtId()
        ));
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
        public ArtifactRevContext getLastRevBySourceType(@NotNull ArtifactRevSourceType type) {
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
        public ArtifactRevSourceType getSourceType() {
            return ArtifactRevSourceType.valueOf(uploadDto.getSource().getSource().getType().name());
        }

        @NotNull
        @Override
        public String getSourceId() {
            return uploadDto.getSource().getSource().getId();
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
        public ArtifactRevSourceType getSourceType() {
            return rev.getSourceType();
        }

        @NotNull
        @Override
        public String getSourceId() {
            return rev.getSourceId();
        }
    }
}
