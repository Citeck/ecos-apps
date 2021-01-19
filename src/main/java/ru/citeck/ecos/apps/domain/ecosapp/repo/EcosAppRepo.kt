package ru.citeck.ecos.apps.domain.ecosapp.repo

import org.springframework.data.jpa.repository.JpaRepository

interface EcosAppRepo : JpaRepository<EcosAppEntity, Long> {

    fun findFirstByExtId(extId: String): EcosAppEntity?

    fun findAllByArtifactsDirIsNotNull(): List<EcosAppEntity>
}
