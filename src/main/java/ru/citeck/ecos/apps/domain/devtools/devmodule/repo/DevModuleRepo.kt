package ru.citeck.ecos.apps.domain.devtools.devmodule.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository

@Repository
interface DevModuleRepo
    : JpaRepository<DevModuleEntity, Long>, JpaSpecificationExecutor<DevModuleEntity> {

    fun findByExtId(extId: String): DevModuleEntity?
}
