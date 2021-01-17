package ru.citeck.ecos.apps.domain.artifact.source.repo

import org.springframework.data.jpa.repository.JpaRepository
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType

interface ArtifactSourceMetaRepo : JpaRepository<ArtifactSourceMetaEntity, Long> {

    fun findFirstBySourceTypeAndSourceId(sourceType: ArtifactSourceType, sourceId: String): ArtifactSourceMetaEntity?
}

