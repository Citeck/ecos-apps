package ru.citeck.ecos.apps.domain.artifact.type.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EcosArtifactTypesRepo extends JpaRepository<EcosArtifactTypesEntity, Long> {

    EcosArtifactTypesEntity findBySource(String source);

    @Query("SELECT max(t.lastModifiedDate) FROM EcosArtifactTypesEntity t")
    Instant getLastModified();

    @Query("SELECT DISTINCT t.source FROM EcosArtifactTypesEntity t")
    List<String> getSources();
}
