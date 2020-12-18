package ru.citeck.ecos.apps.domain.content.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity;

@Repository
public interface EcosContentRepo extends JpaRepository<EcosContentEntity, Long> {

    EcosContentEntity findFirstByHashAndSize(String hash, long size);
}
