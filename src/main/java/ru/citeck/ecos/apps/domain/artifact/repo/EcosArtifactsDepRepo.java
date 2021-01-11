package ru.citeck.ecos.apps.domain.artifact.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EcosArtifactsDepRepo extends JpaRepository<EcosArtifactDepEntity, Long> {

    @Query("SELECT dep FROM EcosArtifactDepEntity dep " +
           "JOIN dep.target targetModule " +
           "WHERE targetModule.id=?1")
    List<EcosArtifactDepEntity> getDepsByTarget(long targetModuleId);
}
