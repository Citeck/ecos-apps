package ru.citeck.ecos.apps.domain.ecosapp.repo

import org.springframework.data.jpa.repository.JpaRepository

interface EcosAppArtifactRepo : JpaRepository<EcosAppArtifactEntity, Long> {

    fun findFirstByExtId(extId: String) : EcosAppArtifactEntity?
}
