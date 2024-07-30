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

        return searchConv.findAll(artifactsRepo, preparePredicate(predicate), maxItems, skipCount, sort)
            .stream()
            .map(EcosArtifactEntity::getLastRev)
            .collect(Collectors.toList());
    }

    public long getCount(Predicate predicate) {
        return searchConv.getCount(artifactsRepo, preparePredicate(predicate));
    }

    public void removeEcosApp(String ecosAppId) {

        List<EcosArtifactEntity> currentArtifacts = artifactsRepo.findAllByEcosApp(ecosAppId);
        for (EcosArtifactEntity artifact : currentArtifacts) {
            artifact.setEcosApp(null);
            artifactsRepo.save(artifact);
        }
    }

    public List<EcosArtifactEntity> getArtifactsByEcosApp(String ecosAppId) {
        return artifactsRepo.getArtifactsByEcosApp(ecosAppId);
    }

    public List<EcosArtifactEntity> getDependentModules(ArtifactRef targetRef) {

        EcosArtifactEntity moduleEntity = artifactsRepo.getByExtId(targetRef.getType(), targetRef.getId());
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
            .map(it -> Optional.ofNullable(artifactsRepo.getByExtId(it.getType(), it.getId())))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    public EcosArtifactEntity getArtifact(ArtifactRef ref) {
        return artifactsRepo.getByExtId(ref.getType(), ref.getId());
    }

    public List<EcosArtifactRevEntity> getArtifactRevisionsSince(ArtifactRef ref, Instant since, int skip, int max) {
        if (max <= 0) {
            return Collections.emptyList();
        }
        int page = skip / max;
        return artifactsRevRepo.getArtifactRevisionsSince(
            ref.getType(),
            ref.getId(),
            since,
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

    private Specification<EcosArtifactEntity> getNonDeletedWithLastRevSpec() {
        Specification<EcosArtifactEntity> spec = (root, query, builder) -> builder.isNotNull(root.get("lastRev"));
        spec = spec.and((root, query, builder) -> builder.notEqual(root.get("deleted"), true));
        return spec;
    }

    private Predicate preparePredicate(Predicate predicate) {

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

        return AndPredicate.of(andPredicates);
    }
}
