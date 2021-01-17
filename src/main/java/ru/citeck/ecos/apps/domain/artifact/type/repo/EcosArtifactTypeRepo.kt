package ru.citeck.ecos.apps.domain.artifact.type.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface EcosArtifactTypeRepo : JpaRepository<EcosArtifactTypeEntity, Long>,
                                 JpaSpecificationExecutor<EcosArtifactTypeEntity> {

    @Query("SELECT max(t.lastModifiedDate) FROM EcosArtifactTypeEntity t")
    fun getLastModified(): Instant?

    fun findFirstByExtId(extId: String): EcosArtifactTypeEntity?

    @Query("SELECT distinct(t.extId) from EcosArtifactTypeEntity t WHERE t.internal = false")
    fun findNonInternalTypeIds(): Set<String>

    @Query("SELECT distinct(t.extId) from EcosArtifactTypeEntity t WHERE t.recordsSourceId <> ''")
    fun findTypeIdsWithRecordsSourceId(): Set<String>

    fun findFirstByAppNameAndRecordsSourceId(appName: String, recordsSourceId: String): EcosArtifactTypeEntity?

    fun findAllByAppName(appName: String): List<EcosArtifactTypeEntity>

    @Query("SELECT distinct(t.extId) from EcosArtifactTypeEntity t")
    fun getAllTypeIds(): Set<String>
}
