package ru.citeck.ecos.apps.domain.artifactpatch.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EcosArtifactPatchRepo extends JpaRepository<EcosArtifactPatchEntity, Long>,
                                             JpaSpecificationExecutor<EcosArtifactPatchEntity> {

    Optional<EcosArtifactPatchEntity> findFirstByExtId(String extId);

    List<EcosArtifactPatchEntity> findAllByTarget(String target);
}
