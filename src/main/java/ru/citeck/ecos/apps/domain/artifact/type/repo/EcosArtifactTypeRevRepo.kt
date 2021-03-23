package ru.citeck.ecos.apps.domain.artifact.type.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EcosArtifactTypeRevRepo : JpaRepository<EcosArtifactTypeRevEntity?, Long?>
