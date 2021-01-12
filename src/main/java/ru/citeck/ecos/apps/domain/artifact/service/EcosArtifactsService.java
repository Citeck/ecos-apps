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
            artifactEntity.setDeployStatus(DeployStatus.DRAFT);
        }

        artifactsRepo.save(artifactEntity);

        return true;
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
                                                                Collection<ArtifactRef> artifacts) {

        Set<EcosArtifactDepEntity> dependencies = new HashSet<>();

        Set<ArtifactRef> artifactsSet = new HashSet<>(artifacts);

        for (ArtifactRef ref : artifactsSet) {

            EcosArtifactEntity artifactEntity = artifactsRepo.getByExtId(ref.getType(), ref.getId());
            if (artifactEntity == null) {
                artifactEntity = new EcosArtifactEntity();
                artifactEntity.setExtId(ref.getId());
                artifactEntity.setType(ref.getType());
                artifactEntity = artifactsRepo.save(artifactEntity);
            }

            EcosArtifactDepEntity depEntity = new EcosArtifactDepEntity();
            depEntity.setSource(baseEntity);
            depEntity.setTarget(artifactEntity);
            dependencies.add(depEntity);
        }

        return new HashSet<>(dependencies);
    }

    private MLText toNotNullMLText(String value) {
        if (StringUtils.isBlank(value)) {
            return new MLText();
        }
        return Json.getMapper().read(value, MLText.class);
    }

    public Map<String, List<String>> updateAndGetArtifactsToDeploy(List<String> types) {
        return Collections.emptyMap();
    }
/*
    public void updateModule(ArtifactRef moduleRef) {

        EcosArtifactEntity module = dao.getModule(moduleRef);
        if (module != null) {

            try {
                EcosArtifactRevEntity lastRev = module.getLastRev();
                byte[] lastRevData = lastRev.getContent().getData();
                Object moduleObj = localModulesService.readFromBytes(lastRevData, moduleRef.getType());

                uploadEcosAppArtifacts(
                    "update-modules",
                    Collections.singletonList(moduleObj),
                    moduleRef.getType(),
                    null
                );

            } catch (Exception e) {
                log.error("Module can't be updated. Module: '" + moduleRef + "'", e);
            }
        }
    }*/
/*
    synchronized public void uploadEcosAppArtifacts(String source,
                                                    List<Object> modules,
                                                    String type,
                                                    @Nullable String ecosApp) {

        if (log.isDebugEnabled()) {
            log.debug("Modules to upload: " + modules.size() + " with type '" + type + "' and source '" + source + "'");
        }

        if (modules.isEmpty()) {
            return;
        }

        String app = ecosArtifactTypesService.getAppByModuleType(type);
        if (app.isEmpty()) {
            log.info("Application is not defined for type " + type + ". Modules can't be uploaded");
            return;
        }

        List<ModuleWithMeta<Object>> modulesMeta = remoteModulesService.prepareToDeploy(app, type, modules);
        if (modules.size() != modulesMeta.size()) {
            log.info("Modules count was changed by target app. Before: "
                + modules.size() + " After: " + modulesMeta.size());
        }

        List<Object> patchedModules = new ArrayList<>();

        for (ModuleWithMeta<Object> module : modulesMeta) {

            if (log.isDebugEnabled()) {
                log.debug("Upload module " + module.getMeta());
            }

            dao.uploadModule(source, type, module, false, ecosApp);

            ArtifactRef moduleRef = ArtifactRef.create(type, module.getMeta().getId());
            List<ArtifactPatchDto> patches = artifactPatchService.getPatches(moduleRef);
            boolean wasPatched = false;
            if (!patches.isEmpty()) {
                Object patchedData = artifactPatchService.applyPatches(module.getModule(), moduleRef, patches);
                if (!Objects.equals(patchedData, module.getModule())) {
                    patchedModules.add(patchedData);
                    wasPatched = true;
                }
            }
            if (!wasPatched) {
                dao.removePatchedRev(moduleRef);
            }
        }

        if (!patchedModules.isEmpty()) {

            List<ModuleWithMeta<Object>> patchedMeta = remoteModulesService.prepareToDeploy(app, type, patchedModules);
            if (patchedModules.size() != patchedMeta.size()) {
                log.info("Patched modules count was changed by target app. Before: "
                    + patchedModules.size() + " After: " + patchedMeta.size());
            }

            for (ModuleWithMeta<Object> module : patchedMeta) {
                dao.uploadPatchedModule(type, module);
            }
        }

        for (ModuleWithMeta<Object> module : modulesMeta) {

            EcosArtifactEntity entity = dao.getModule(ArtifactRef.create(type, module.getMeta().getId()));

            if (entity != null && !DeployStatus.DEPLOYED.equals(entity.getDeployStatus())) {
                tryToDeploy(entity);
            }
        }
    }*/
/*
    private void tryToDeploy(EcosArtifactEntity moduleEntity) {

        ArtifactRef ref = ArtifactRef.create(moduleEntity.getType(), moduleEntity.getExtId());

        if (moduleEntity.getDependencies()
            .stream()
            .map(EcosArtifactDepEntity::getTarget)
            .anyMatch(d -> !DeployStatus.DEPLOYED.equals(d.getDeployStatus()))) {

            moduleEntity.setDeployStatus(DeployStatus.DEPS_WAITING);
            log.info("Module " + ref + " can't be deployed yet. Dependencies waiting");

            dao.save(moduleEntity);

        } else {

            String type = moduleEntity.getType();
            String appName = ecosArtifactTypesService.getAppByModuleType(type);

            EcosArtifactRevEntity revToDeploy = moduleEntity.getPatchedRev();
            if (revToDeploy == null) {
                revToDeploy = moduleEntity.getLastRev();
            }
            if (revToDeploy != null) {

                EcosContentEntity content = revToDeploy.getContent();

                if (content != null) {

                    Object module = localModulesService.readFromBytes(content.getData(), type);
                    List<CommandError> errors = remoteModulesService.deployModule(appName, type, module);

                    if (errors.isEmpty()) {

                        moduleEntity.setDeployStatus(DeployStatus.DEPLOYED);
                        moduleEntity.setDeployMsg("");

                        dao.save(moduleEntity);

                        tryToDeployDependentModules(moduleEntity);

                    } else {

                        String msg = errors.stream()
                            .map(CommandError::getMessage)
                            .collect(Collectors.joining("|"));

                        log.info("Module " + ref + " deploy failed. Msg: " + msg);

                        moduleEntity.setDeployStatus(DeployStatus.DEPLOY_FAILED);
                        moduleEntity.setDeployMsg(msg);

                        dao.save(moduleEntity);
                    }
                } else {

                    log.info("Module " + ref + " can't be deployed. Content is missing");
                }
            } else {

                log.info("Module " + ref + " can't be deployed. Last revision is missing");
            }
        }
    }

    private void tryToDeployDependentModules(EcosArtifactEntity module) {

        ArtifactRef moduleRef = ArtifactRef.create(module.getType(), module.getExtId());
        List<EcosArtifactEntity> modules = dao.getDependentModules(moduleRef);

        for (EcosArtifactEntity moduleFromDep : modules) {

            DeployStatus depStatus = moduleFromDep.getDeployStatus();

            if (DeployStatus.DEPS_WAITING.equals(depStatus)
                || DeployStatus.DEPLOY_FAILED.equals(depStatus)) {

                tryToDeploy(moduleFromDep);
            }
        }
    }



    synchronized public List<EcosArtifact> getModules(String type, int skipCount, int maxItems) {

        return dao.getModulesLastRev(type, skipCount, maxItems)
            .stream()
            .map(this::toModule)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }


 */

    synchronized public List<EcosArtifact> getAllArtifacts(int skipCount, int maxItems) {
        return dao.getAllLastRevisions(skipCount, maxItems)
            .stream()
            .map(this::toModule)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    synchronized public long getAllArtifactsCount(Predicate predicate) {
        return dao.getCount(predicate);
    }

    synchronized public List<EcosArtifact> getAllArtifacts(Predicate predicate, int skipCount, int maxItems) {
        return dao.getAllLastRevisions(predicate, skipCount, maxItems)
            .stream()
            .map(this::toModule)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    @Nullable
    public EcosArtifact getLastArtifact(ArtifactRef moduleRef) {
        return toModule(dao.getLastModuleRev(moduleRef)).orElse(null);
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
        dao.removeEcosApp(ecosAppId);
    }

    @Nullable
    synchronized public EcosArtifactRev getLastArtifactRev(ArtifactRef moduleRef) {
        EcosArtifactRevEntity lastModuleRev = dao.getLastModuleRev(moduleRef);
        if (lastModuleRev == null) {
            return null;
        }
        return new EcosArtifactDb(lastModuleRev);
    }

    synchronized public EcosArtifactRev getLastArtifactRev(ArtifactRef moduleRef, String source) {
        return new EcosArtifactDb(dao.getLastModuleRev(moduleRef, source));
    }

    synchronized public EcosArtifactRev getLastModuleRevByKey(String type, String key) {
        EcosArtifactRevEntity rev = dao.getLastModuleRevByKey(type, key);
        return rev != null ? new EcosArtifactDb(rev) : null;
    }

    synchronized public DeployStatus getDeployStatus(ArtifactRef moduleRef) {
        EcosArtifactEntity module = dao.getModule(moduleRef);
        return module.getDeployStatus();
    }

    synchronized public ArtifactPublishState getDeployState(ArtifactRef moduleRef) {
        EcosArtifactEntity module = dao.getModule(moduleRef);
        return new ArtifactPublishState(module.getDeployStatus(), module.getDeployMsg());
    }

    synchronized public EcosArtifactRev getModuleRevision(String id) {
        return new EcosArtifactDb(dao.getModuleRev(id));
    }

    private Optional<EcosArtifact> toModule(EcosArtifactRevEntity entity) {

        if (entity == null) {
            return Optional.empty();
        }

        String type = entity.getModule().getType();
        byte[] content = entity.getContent().getData();

        if (ecosArtifactTypesService.isTypeRegistered(type)) {
            return Optional.of(new EcosArtifact(
                entity.getModule().getExtId(),
                artifactsService.readArtifactFromBytes(type, content),
                type,
                Json.getMapper().read(entity.getModule().getName(), MLText.class),
                DataValue.create(entity.getModule().getTags()).asStrList()
            ));
        }
        return Optional.empty();
    }

    @RequiredArgsConstructor
    private class EcosArtifactContext implements ArtifactContext {

        final EcosArtifactEntity artifactEntity;

        NewRevContext newRev;

        @NotNull
        @Override
        public String getEcosApp() {
            return artifactEntity.getEcosApp();
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
