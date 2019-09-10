package ru.citeck.ecos.apps.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.EcosContentEntity;

@Repository
public interface EcosContentRepo extends JpaRepository<EcosContentEntity, Long> {

    @Query("SELECT content FROM EcosContentEntity content WHERE content.hash = ?1 AND content.size = ?2")
    EcosContentEntity findContent(String hash, long size);
}
