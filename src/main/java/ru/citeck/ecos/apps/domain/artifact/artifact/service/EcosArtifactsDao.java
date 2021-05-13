package ru.citeck.ecos.apps.domain.artifact.artifact.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.artifact.ArtifactRef;
import ru.citeck.ecos.apps.domain.artifact.artifact.repo.*;
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService;
import ru.citeck.ecos.records2.predicate.PredicateUtils;
import ru.citeck.ecos.records2.predicate.model.Predicate;

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
        return artifactsRepo.getModulesLastRev(type, PageRequest.of(page, maxItems));
    }

    public List<EcosArtifactRevEntity> getAllLastRevisions(int skipCount, int maxItems) {

        int page = skipCount / maxItems;
        return artifactsRepo.findAll(PageRequest.of(page, maxItems, Sort.by(Sort.Order.desc("id"))))
            .stream()
            .map(EcosArtifactEntity::getLastRev)
            .collect(Collectors.toList());
    }

    public List<EcosArtifactRevEntity> getAllLastRevisions(Predicate predicate, int skipCount, int maxItems) {

        Specification<EcosArtifactEntity> spec = toSpec(predicate);

        int page = skipCount / maxItems;
        return artifactsRepo.findAll(spec, PageRequest.of(page, maxItems, Sort.by(Sort.Order.desc("id"))))
            .stream()
            .map(EcosArtifactEntity::getLastRev)
            .collect(Collectors.toList());
    }

    public long getCount(Predicate predicate) {
        Specification<EcosArtifactEntity> spec = toSpec(predicate);
        return artifactsRepo.count(spec);
    }

    public void removeEcosApp(String ecosAppId) {

        List<EcosArtifactEntity> currentArtifacts = artifactsRepo.findAllByEcosApp(ecosAppId);
        for (EcosArtifactEntity artifact : currentArtifacts) {
            artifact.setEcosApp(null);
            artifactsRepo.save(artifact);
        }
    }

    public List<EcosArtifactEntity> getDependentModules(ArtifactRef targetRef) {

        EcosArtifactEntity moduleEntity = artifactsRepo.getByExtId(targetRef.getType(), targetRef.getId());
        List<EcosArtifactDepEntity> depsByTarget = artifactsDepRepo.getDepsByTarget(moduleEntity.getId());

        return depsByTarget.stream()
            .map(EcosArtifactDepEntity::getSource)
            .collect(Collectors.toList());
    }

    public EcosArtifactRevEntity getLastArtifactRev(String type, String id) {
        return getLastArtifactRev(ArtifactRef.create(type, id));
    }

    public EcosArtifactRevEntity getLastArtifactRev(ArtifactRef moduleRef) {
        EcosArtifactEntity artifact = getArtifact(moduleRef);
        if (artifact == null) {
            return null;
        }
        return artifact.getLastRev();
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

    private Specification<EcosArtifactEntity> toSpec(Predicate predicate) {

        PredicateDto predicateDto = PredicateUtils.convertToDto(predicate, PredicateDto.class);
        Specification<EcosArtifactEntity> spec = null;

        if (StringUtils.isNotBlank(predicateDto.name)) {
            spec = (root, query, builder) ->
                builder.like(builder.lower(root.get("name")), "%" + predicateDto.name.toLowerCase() + "%");
        }
        if (StringUtils.isNotBlank(predicateDto.moduleId)) {
            Specification<EcosArtifactEntity> idSpec = (root, query, builder) ->
                builder.like(builder.lower(root.get("extId")), "%" + predicateDto.moduleId.toLowerCase() + "%");
            spec = spec != null ? spec.or(idSpec) : idSpec;
        }
        if (StringUtils.isNotBlank(predicateDto.type)) {
            Specification<EcosArtifactEntity> typeSpec = (root, query, builder) ->
                builder.like(builder.lower(root.get("type")), "%" + predicateDto.type.toLowerCase() + "%");
            spec = spec != null ? spec.or(typeSpec) : typeSpec;
        }
        String tags = predicateDto.tags;
        if (StringUtils.isBlank(tags)) {
            tags = predicateDto.tagsStr;
        }
        String finalTags = tags;
        if (StringUtils.isNotBlank(finalTags)) {
            Specification<EcosArtifactEntity> tagsSpec = (root, query, builder) ->
                builder.like(
                    builder.lower(root.get("tags")), "%" + finalTags.toLowerCase() + "%"
                );
            spec = spec != null ? spec.or(tagsSpec) : tagsSpec;
        }

        Specification<EcosArtifactEntity> sourceIdFilter = (root, query, builder) ->
            root.get("type").in(ecosArtifactTypesService.getNonInternalTypes());

        if (spec != null) {
            spec = spec.and(sourceIdFilter);
        } else {
            spec = sourceIdFilter;
        }

        if (predicateDto.system != null) {
            Specification<EcosArtifactEntity> systemSpec;
            if (!predicateDto.system) {
                systemSpec = (root, query, builder) -> builder.not(builder.equal(root.get("system"), true));
            } else {
                systemSpec = (root, query, builder) -> builder.equal(root.get("system"), true);
            }
            spec = spec.and(systemSpec);
        }

        return spec.and((root, query, builder) -> builder.isNotNull(root.get("lastRev")))
            .and((root, query, builder) -> builder.notEqual(root.get("deleted"), true));
    }

    @Data
    public static class PredicateDto {
        private String name;
        private String type;
        private String tags;
        private String tagsStr;
        private String moduleId;
        private Boolean system;
    }
}
