package ru.citeck.ecos.apps.domain.artifact.application.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceInfo
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceType
import ru.citeck.ecos.apps.app.service.remote.RemoteAppService
import ru.citeck.ecos.apps.app.service.remote.RemoteAppStatus
import ru.citeck.ecos.apps.artifact.ArtifactService
import ru.citeck.ecos.apps.artifact.type.ArtifactTypeService
import ru.citeck.ecos.apps.domain.artifact.artifact.service.DeployError
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.artifact.service.deploy.ArtifactDeployer
import ru.citeck.ecos.apps.domain.artifact.source.service.ArtifactsSource
import ru.citeck.ecos.apps.domain.artifact.source.service.EcosArtifactsSourcesService
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService
import ru.citeck.ecos.commons.io.file.EcosFile
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

@Service
class EcosApplicationsService(
    private val ecosArtifactsSourcesService: EcosArtifactsSourcesService,
    private val ecosArtifactTypesService: EcosArtifactTypesService,
    private val ecosArtifactsService: EcosArtifactsService,
    private val artifactTypeService: ArtifactTypeService,
    private val remoteAppService: RemoteAppService,
    private val artifactService: ArtifactService
) {

    companion object {
        val log = KotlinLogging.logger {}
    }

    private val sources = ConcurrentHashMap<SourceKey, AppArtifactsSource>()
    private val typesSources = ConcurrentHashMap<String, RemoteAppStatus>()
    private val deployers = ConcurrentHashMap<String, AppArtifactsDeployer>()

    @Synchronized
    fun updateApps(appsStatus: List<RemoteAppStatus>) {

        val appsByName = HashMap<String, MutableList<RemoteAppStatus>>()

        appsStatus.forEach { appsByName.computeIfAbsent(it.appName) { ArrayList() }.add(it)  }

        val newSources = mutableMapOf<SourceKey, AppArtifactsSourceInfo>()
        val newTypesSources = mutableMapOf<String, RemoteAppStatus>()

        appsByName.forEach { (appName, appStatus) ->

            appStatus.forEach { appInstance ->

                val typesSource = newTypesSources[appInstance.appName]
                if (typesSource == null
                    || appInstance.status.typesLastModified.isAfter(typesSource.status.typesLastModified)) {

                    newTypesSources[appInstance.appName] = appInstance
                }

                appInstance.status.sources.forEach { source ->

                    val key = SourceKey(appName,  source.id)
                    val currentSource = newSources[key]

                    if (currentSource == null) {
                        newSources[key] = AppArtifactsSourceInfo(appInstance, source)
                    } else if (currentSource.sourceInfo.lastModified.isBefore(source.lastModified)) {
                        newSources[key] = AppArtifactsSourceInfo(appInstance, source)
                    }
                }
            }
        }

        val deployersToRemove = mutableListOf<String>()
        deployers.keys.forEach { appName ->
            if (!newTypesSources.containsKey(appName)) {
                deployersToRemove.add(appName)
            }
        }
        deployersToRemove.forEach { deployers.remove(it) }

        newTypesSources.values.forEach {

            val currentSource = typesSources[it.appName]

            if (currentSource == null
                    || currentSource.status.typesLastModified.isBefore(it.status.typesLastModified)) {

                try {
                    val typesDir = remoteAppService.getArtifactTypesDir(it.appInstanceId)
                    ecosArtifactTypesService.registerTypes(it.appName, typesDir, it.status.typesLastModified)

                    deployers.remove(it.appName)
                    typesSources[it.appName] = it

                } catch (e: Exception) {
                    log.error(e) { "Types dir loading error. App: $it" }
                }
            }

            if (!deployers.containsKey(it.appName)) {

                val supportedTypesByController = ecosArtifactTypesService.getTypesByAppName(it.appName)
                    .map { type -> type.getId() }
                val supportedTypes = supportedTypesByController.intersect(it.status.supportedTypes).toList()

                if (supportedTypes.isNotEmpty()) {

                    log.info {
                        "Register deployer for ${it.appName} " +
                            "- ${it.appInstanceId} with supported types: $supportedTypes"
                    }

                    deployers[it.appName] = AppArtifactsDeployer(
                        it.appName,
                        it.appInstanceId,
                        supportedTypes,
                        remoteAppService
                    )
                }
            }
        }

        val sourcesToRemove = mutableSetOf<SourceKey>()

        sources.forEach { (key, appSource) ->
            val newSource = newSources[key]
            if (newSource == null || newSource.sourceInfo.lastModified.isAfter(appSource.getLastModified())) {
                sourcesToRemove.add(key)
            }
        }

        sourcesToRemove.forEach {
            val source = sources.remove(it)
            if (source != null) {
                ecosArtifactsSourcesService.removeSource(source)
            }
        }

        newSources.forEach { (key, appSourceInfo) ->

            val currentSource = sources[key]
            if (currentSource == null || currentSource.getLastModified()
                    .isBefore(appSourceInfo.sourceInfo.lastModified)) {

                val appKey = AppKey(appSourceInfo.appStatus.appName, appSourceInfo.appStatus.appInstanceId)
                val appSource = AppArtifactsSource(
                    appKey,
                    appSourceInfo.sourceInfo,
                    remoteAppService,
                    artifactService,
                    artifactTypeService
                )
                sources[key] = appSource

                log.info { "Add application artifacts source. App: $appKey Source: ${appSourceInfo.sourceInfo}" }

                ecosArtifactsSourcesService.addSource(appSource)
            }
        }
    }

    fun deployArtifacts() {
        deployers.values.forEach {
            try {
                var iter = -1
                while (++iter < 10) {
                    if (!ecosArtifactsService.deployArtifacts(it)) {
                        break
                    }
                }
            } catch (e: Exception) {
                log.error(e) { "Deploing failed for app name: ${it.appName} instanceId: ${it.instanceId}" }
            }
        }
    }

    class AppArtifactsDeployer(
        val appName: String,
        val instanceId: String,
        private val supportedTypes: List<String>,
        private val remoteAppService: RemoteAppService
    ) : ArtifactDeployer {

        override fun deploy(type: String, artifact: ByteArray): List<DeployError> {

            val commandErrors = remoteAppService.deployArtifact(instanceId, type, artifact)
            return commandErrors.stream()
                .map { DeployError(it.type, it.message, it.stackTrace) }
                .collect(Collectors.toList())
        }

        override fun getSupportedTypes(): List<String> {
            return supportedTypes
        }
    }

    private data class AppArtifactsSourceInfo(
        val appStatus: RemoteAppStatus,
        val sourceInfo: ArtifactSourceInfo
    )

    private class AppArtifactsSource(
        val appKey: AppKey,
        val sourceInfo: ArtifactSourceInfo,
        val remoteAppService: RemoteAppService,
        val artifactsService: ArtifactService,
        val artifactTypesService: ArtifactTypeService
    ) : ArtifactsSource {

        override fun getArtifacts(typesDir: EcosFile, since: Instant): Map<String, List<Any>> {
            val artifactsDir = remoteAppService.getArtifactsDir(appKey.instanceId, sourceInfo.id, typesDir, since)
            return artifactsService.readArtifacts(artifactsDir, artifactTypesService.loadTypes(typesDir))
        }

        override fun getLastModified(): Instant {
            return sourceInfo.lastModified
        }

        override fun getSourceType(): ArtifactSourceType {
            return sourceInfo.type
        }

        override fun getId(): String {
            return appKey.appName
        }
    }

    private data class AppKey(
        val appName: String,
        val instanceId: String
    )

    private data class SourceKey(
        val appName: String,
        val sourceId: String
    )
}
