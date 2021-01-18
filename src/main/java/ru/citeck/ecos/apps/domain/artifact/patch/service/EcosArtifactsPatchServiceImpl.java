package ru.citeck.ecos.apps.domain.artifact.patch.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.apps.artifact.ArtifactRef;
import ru.citeck.ecos.apps.artifact.ArtifactService;
import ru.citeck.ecos.apps.artifact.controller.patch.ArtifactPatch;
import ru.citeck.ecos.apps.domain.artifact.patch.dto.ArtifactPatchDto;
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsPatchService;
import ru.citeck.ecos.apps.domain.artifact.patch.repo.EcosArtifactPatchEntity;
import ru.citeck.ecos.apps.domain.artifact.patch.repo.EcosArtifactPatchRepo;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.records2.predicate.PredicateUtils;
import ru.citeck.ecos.records2.predicate.model.Predicate;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EcosArtifactsPatchServiceImpl implements EcosArtifactsPatchService {

    private final EcosArtifactPatchRepo patchRepo;
    private final ArtifactService artifactService;

    private final List<Consumer<ArtifactPatchDto>> changeListeners = new CopyOnWriteArrayList<>();

    public List<ArtifactPatchDto> getAll(int max, int skip, Predicate predicate) {

        PageRequest page = PageRequest.of(skip / max, max, Sort.by(Sort.Direction.DESC, "id"));

        Page<EcosArtifactPatchEntity> modules;
        if (predicate == null) {
            modules = patchRepo.findAll(page);
        } else {
            modules = patchRepo.findAll(toSpec(predicate), page);
        }
        return modules.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    public int getCount(Predicate predicate) {
        if (predicate == null) {
            return getCount();
        }
        Specification<EcosArtifactPatchEntity> spec = toSpec(predicate);
        return spec != null ? (int) patchRepo.count(spec) : getCount();
    }

    public int getCount() {
        return (int) patchRepo.count();
    }

    public Optional<ArtifactPatchDto> getPatchById(String id) {
        return patchRepo.findFirstByExtId(id).map(this::toDto);
    }

    public ArtifactPatchDto save(ArtifactPatchDto patch) {

        ArtifactPatchDto current = toDto(patchRepo.findFirstByExtId(patch.getId()).orElse(null));

        if (!Objects.equals(current, patch)) {

            ArtifactPatchDto result = toDto(patchRepo.save(toEntity(patch)));
            changeListeners.forEach(it -> it.accept(result));
            return result;
        }

        return current;
    }

    public void delete(String id) {

        EcosArtifactPatchEntity entity = patchRepo.findFirstByExtId(id).orElse(null);

        if (entity != null) {
            ArtifactPatchDto dto = toDto(entity);
            patchRepo.delete(entity);
            changeListeners.forEach(it -> it.accept(dto));
        }
    }

    @NotNull
    @Override
    public Object applyPatches(@NotNull Object artifact,
                               @NotNull ArtifactRef artifactRef,
                               @NotNull List<? extends ArtifactPatchDto> patches) {

        List<ArtifactPatch> modulePatches = Json.getMapper().convert(
            patches,
            Json.getMapper().getListType(ArtifactPatch.class)
        );

        if (modulePatches == null || modulePatches.isEmpty()) {
            return artifact;
        }

        log.info("Apply " + modulePatches.size() + " patches to " + artifactRef);

        return artifactService.applyPatches(artifactRef.getType(), artifact, modulePatches);
    }

    @NotNull
    public List<ArtifactPatchDto> getPatches(ArtifactRef artifactRef) {

        List<EcosArtifactPatchEntity> patchEntities = patchRepo.findAllByTarget(artifactRef.toString());
        return patchEntities.stream()
                .map(this::toDto)
                .sorted(Comparator.comparingDouble(ArtifactPatchDto::getOrder))
                .collect(Collectors.toList());
    }

    public void addListener(Consumer<ArtifactPatchDto> listener) {
        this.changeListeners.add(listener);
    }

    private ArtifactPatchDto toDto(EcosArtifactPatchEntity entity) {

        if (entity == null) {
            return null;
        }

        MLText name = Json.getMapper().read(entity.getName(), MLText.class);
        if (name == null) {
            name = new MLText("");
        }
        ObjectData config = Json.getMapper().read(entity.getConfig(), ObjectData.class);
        if (config == null) {
            config = ObjectData.create();
        }

        ArtifactPatchDto result = new ArtifactPatchDto();
        result.setId(entity.getExtId());
        result.setConfig(config);
        result.setName(name);
        result.setOrder(entity.getOrder());
        result.setTarget(ArtifactRef.valueOf(entity.getTarget()));
        result.setType(entity.getType());

        return result;
    }

    private EcosArtifactPatchEntity toEntity(ArtifactPatchDto patch) {

        EcosArtifactPatchEntity entity = patchRepo.findFirstByExtId(patch.getId()).orElse(null);
        if (entity == null) {
            entity = new EcosArtifactPatchEntity();
            entity.setExtId(patch.getId());
        }

        entity.setConfig(Json.getMapper().toString(patch.getConfig()));
        entity.setName(Json.getMapper().toString(patch.getName()));
        entity.setOrder(patch.getOrder());
        entity.setTarget(patch.getTarget().toString());
        entity.setType(patch.getType());

        return entity;
    }


    private Specification<EcosArtifactPatchEntity> toSpec(Predicate predicate) {

        PredicateDto predicateDto = PredicateUtils.convertToDto(predicate, PredicateDto.class);
        Specification<EcosArtifactPatchEntity> spec = null;

        if (StringUtils.isNotBlank(predicateDto.name)) {
            spec = (root, query, builder) ->
                builder.like(builder.lower(root.get("name")), "%" + predicateDto.name.toLowerCase() + "%");
        }
        if (StringUtils.isNotBlank(predicateDto.moduleId)) {
            Specification<EcosArtifactPatchEntity> idSpec = (root, query, builder) ->
                builder.like(builder.lower(root.get("extId")), "%" + predicateDto.moduleId.toLowerCase() + "%");
            spec = spec != null ? spec.or(idSpec) : idSpec;
        }

        return spec;
    }

    @Data
    public static class PredicateDto {
        private String name;
        private String moduleId;
    }
}
