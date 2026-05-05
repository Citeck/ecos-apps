package ru.citeck.ecos.apps.domain.artifact.artifact.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.artifact.ArtifactRef;
import ru.citeck.ecos.apps.domain.artifact.artifact.repo.*;
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService;
import ru.citeck.ecos.context.lib.auth.AuthContext;
import ru.citeck.ecos.model.lib.utils.ModelUtils;
import ru.citeck.ecos.model.lib.workspace.WorkspaceService;
import ru.citeck.ecos.records2.predicate.PredicateUtils;
import ru.citeck.ecos.records2.predicate.model.*;
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy;
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverter;
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverterFactory;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EcosArtifactsDao {

    private final EcosArtifactsRepo artifactsRepo;
    private final EcosArtifactsRevRepo artifactsRevRepo;
    private final EcosArtifactsDepRepo artifactsDepRepo;
    private final EcosArtifactTypesService ecosArtifactTypesService;
    private final JpaSearchConverterFactory jpaSearchConverterFactory;
    private final WorkspaceService workspaceService;

    private JpaSearchConverter<EcosArtifactEntity> searchConv;

    @PostConstruct
    public void init() {
        searchConv = jpaSearchConverterFactory.createConverter(EcosArtifactEntity.class)
            .withAttMapping("sourceType", "lastRev.sourceType")
            .withAttMapping("sourceId", "lastRev.sourceId")
            .withAttMapping("modifiedIso", "lastModifiedDate")
            .withAttMapping("createdIso", "createdDate")
            .withFieldVariants("type", ecosArtifactTypesService::getNonInternalTypesWithName)
            .build();
    }

    public int getArtifactsCount() {
        return (int) artifactsRepo.getCount();
    }

    public int getArtifactsCount(String type) {
        return (int) artifactsRepo.getCount(type);
    }

    public List<EcosArtifactEntity> getArtifactsByType(String type) {
        return artifactsRepo.findAllByType(type);
    }

    public List<EcosArtifactRevEntity> getArtifactsLastRev(String type, int skipCount, int maxItems) {
        int page = skipCount / maxItems;
        return artifactsRepo.getArtifactsLastRev(type, PageRequest.of(page, maxItems));
    }

    public List<EcosArtifactRevEntity> getAllLastRevisions(int skipCount, int maxItems) {

        int page = skipCount / maxItems;
        return artifactsRepo.findAll(
            getNonDeletedWithLastRevSpec(),
            PageRequest.of(page, maxItems, Sort.by(Sort.Order.desc("id")))
        ).stream()
            .map(EcosArtifactEntity::getLastRev)
            .collect(Collectors.toList());
    }

    public List<EcosArtifactRevEntity> getAllLastRevisions(Predicate predicate,
                                                           int maxItems,
                                                           int skipCount,
                                                           List<SortBy> sort) {
        return getAllLastRevisions(predicate, Collections.emptyList(), maxItems, skipCount, sort);
    }

    public List<EcosArtifactRevEntity> getAllLastRevisions(Predicate predicate,
                                                           List<String> workspaces,
                                                           int maxItems,
                                                           int skipCount,
                                                           List<SortBy> sort) {

        return searchConv.findAll(artifactsRepo, preparePredicate(predicate, workspaces), maxItems, skipCount, sort)
            .stream()
            .map(EcosArtifactEntity::getLastRev)
            .collect(Collectors.toList());
    }

    public long getCount(Predicate predicate) {
        return getCount(predicate, Collections.emptyList());
    }

    public long getCount(Predicate predicate, List<String> workspaces) {
        return searchConv.getCount(artifactsRepo, preparePredicate(predicate, workspaces));
    }

    public void removeEcosApp(String ecosAppId, String workspace) {

        List<EcosArtifactEntity> currentArtifacts =
            artifactsRepo.findAllByEcosAppAndWorkspace(ecosAppId, normalizeWorkspace(workspace));
        for (EcosArtifactEntity artifact : currentArtifacts) {
            artifact.setEcosApp(null);
            artifactsRepo.save(artifact);
        }
    }

    public List<EcosArtifactEntity> getArtifactsByEcosApp(String ecosAppId) {
        return artifactsRepo.getArtifactsByEcosApp(ecosAppId);
    }

    public List<EcosArtifactEntity> getArtifactsByEcosApp(String ecosAppId, String workspace) {
        return artifactsRepo.getArtifactsByEcosAppAndWorkspace(ecosAppId, normalizeWorkspace(workspace));
    }

    public List<EcosArtifactEntity> getDependentModules(ArtifactRef targetRef) {

        EcosArtifactEntity moduleEntity = getArtifact(targetRef);
        List<EcosArtifactDepEntity> depsByTarget = artifactsDepRepo.getDepsByTarget(moduleEntity.getId());

        return depsByTarget.stream()
            .map(EcosArtifactDepEntity::getSource)
            .collect(Collectors.toList());
    }

    public EcosArtifactRevEntity getLastArtifactRev(ArtifactRef artifactRef) {
        return getLastArtifactRev(artifactRef, true);
    }

    public EcosArtifactRevEntity getLastArtifactRev(ArtifactRef moduleRef, boolean includePatched) {
        EcosArtifactEntity artifact = getArtifact(moduleRef);
        if (artifact == null) {
            return null;
        }
        if (includePatched) {
            EcosArtifactRevEntity patchedRev = artifact.getPatchedRev();
            if (patchedRev != null) {
                return patchedRev;
            }
        }
        return artifact.getLastRev();
    }

    public List<EcosArtifactEntity> getArtifactsByRefs(List<ArtifactRef> refs) {
        return refs.stream()
            .map(it -> Optional.ofNullable(getArtifact(it)))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    public EcosArtifactEntity getArtifact(ArtifactRef ref) {
        String wsId = resolveWorkspaceId(ref.getWsSysId());
        return artifactsRepo.getByExtId(ref.getType(), ref.getId(), wsId);
    }

    /**
     * Direct workspace-id lookup that bypasses the wsSysId↔wsId round trip.
     * Use when the caller already has the workspace id and the round trip would
     * fail (e.g. virtual workspaces like {@code admin$workspace} that may not
     * yet be materialized in ecos-model when this is called).
     */
    public EcosArtifactEntity getArtifact(String type, String extId, String workspaceId) {
        return artifactsRepo.getByExtId(type, extId, normalizeWorkspace(workspaceId));
    }

    public List<EcosArtifactRevEntity> getArtifactRevisionsSince(ArtifactRef ref, Instant since, int skip, int max) {
        if (max <= 0) {
            return Collections.emptyList();
        }
        int page = skip / max;
        String wsId = resolveWorkspaceId(ref.getWsSysId());
        return artifactsRevRepo.getArtifactRevisionsSince(
            ref.getType(),
            ref.getId(),
            since,
            wsId,
            PageRequest.of(page, max)
        );
    }

    public EcosArtifactEntity save(EcosArtifactEntity entity) {
        return artifactsRepo.save(entity);
    }

    public EcosArtifactRevEntity save(EcosArtifactRevEntity entity) {
        return artifactsRevRepo.save(entity);
    }

    public void delete(EcosArtifactEntity module) {

        if (module != null) {
            module.setExtId(module.getExtId() + "_DELETED_" + module.getId());
            module.setDeleted(true);
            artifactsRepo.save(module);
        }
    }

    public void delete(ArtifactRef ref) {
        delete(getArtifact(ref));
    }

    /**
     * Returns "" for null/blank/default workspace, workspace ID as-is otherwise.
     * Note: ecos-apps deliberately does NOT collapse `admin$*` workspaces to "" the way
     * `WorkspaceService.isWorkspaceWithGlobalEntities` does. Artifacts are stored per
     * workspace (including admin$X) so deploy meta carries the actual target workspace
     * to downstream services.
     */
    public String normalizeWorkspace(String workspace) {
        if (workspace == null || workspace.isBlank()
            || ModelUtils.DEFAULT_WORKSPACE_ID.equals(workspace)) {
            return "";
        }
        return workspace;
    }

    /**
     * Resolves wsSysId from ArtifactRef to workspace ID for DB lookup.
     */
    public String resolveWorkspaceId(String wsSysId) {
        if (wsSysId == null || wsSysId.isEmpty()) {
            return "";
        }
        try {
            String wsId = workspaceService.getWorkspaceIdBySystemId(wsSysId);
            return normalizeWorkspace(wsId);
        } catch (Exception e) {
            log.warn("Failed to resolve workspace ID for wsSysId '{}': {}", wsSysId, e.getMessage());
            return "";
        }
    }

    /**
     * Converts workspace ID (from DB) to wsSysId for ArtifactRef.
     */
    public String toWsSysId(String workspaceId) {
        if (workspaceId == null || workspaceId.isEmpty()) {
            return "";
        }
        try {
            return workspaceService.getWorkspaceSystemId(workspaceId);
        } catch (Exception e) {
            log.warn("Failed to resolve wsSysId for workspace '{}': {}", workspaceId, e.getMessage());
            return "";
        }
    }

    public ArtifactRef toArtifactRef(EcosArtifactEntity entity) {
        String wsSysId = toWsSysId(entity.getWorkspace());
        return ArtifactRef.create(entity.getType(), entity.getExtId(), wsSysId);
    }

    private Specification<EcosArtifactEntity> getNonDeletedWithLastRevSpec() {
        Specification<EcosArtifactEntity> spec = (root, query, builder) -> builder.isNotNull(root.get("lastRev"));
        spec = spec.and((root, query, builder) -> builder.notEqual(root.get("deleted"), true));
        return spec;
    }

    private Predicate preparePredicate(Predicate predicate, List<String> workspaces) {

        Set<String> attsInPredicate = new HashSet<>();
        predicate = PredicateUtils.mapAttributePredicates(predicate, pred -> {
            attsInPredicate.add(pred.getAttribute());
            if (pred instanceof ValuePredicate && pred.getAttribute().equals("excludeTypes")) {
                ValuePredicate valuePred = (ValuePredicate) pred;
                if (valuePred.getValue().asBoolean()) {
                    return Predicates.not(Predicates.eq("type", "model/type"));
                } else {
                    return null;
                }
            }
            return pred;
        });
        if (attsInPredicate.contains("tagsStr") && !attsInPredicate.contains("tags")) {
            predicate = PredicateUtils.mapAttributePredicates(predicate, pred -> {
                if (pred.getAttribute().equals("tagsStr")) {
                    AttributePredicate copy = pred.copy();
                    copy.setAtt("tags");
                    return copy;
                } else {
                    return pred;
                }
            });
        }

        List<Predicate> andPredicates = new ArrayList<>();
        andPredicates.add(predicate);
        andPredicates.add(Predicates.in("type", ecosArtifactTypesService.getNonInternalTypes()));
        andPredicates.add(Predicates.notEmpty("lastRev"));
        andPredicates.add(Predicates.not(Predicates.eq("deleted", true)));
        andPredicates.add(
            workspaceService.buildAvailableWorkspacesPredicate(AuthContext.getCurrentRunAsAuth(), workspaces)
        );

        return AndPredicate.of(andPredicates);
    }
}
