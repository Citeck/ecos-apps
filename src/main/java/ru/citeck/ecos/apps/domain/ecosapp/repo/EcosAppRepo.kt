package ru.citeck.ecos.apps.domain.ecosapp.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface EcosAppRepo : JpaRepository<EcosAppEntity, Long>, JpaSpecificationExecutor<EcosAppEntity> {

    fun findFirstByExtId(extId: String): EcosAppEntity?

    fun findAllByArtifactsDirIsNotNull(): List<EcosAppEntity>
}
