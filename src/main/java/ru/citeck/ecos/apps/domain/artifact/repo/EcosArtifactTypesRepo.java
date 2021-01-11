package ru.citeck.ecos.apps.domain.artifact.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity;

import java.util.List;

@Repository
public interface EcosArtifactTypesRepo extends JpaRepository<EcosArtifactTypesEntity, Long> {

    EcosArtifactTypesEntity findBySource(String source);

    EcosArtifactTypesEntity findBySourceAndContent(String source, EcosContentEntity content);

    EcosArtifactTypesEntity findFirstBySourceOrderByCreatedDateDesc(String source);

    @Query("SELECT DISTINCT t.source FROM EcosArtifactTypesEntity t")
    List<String> getSources();
}
