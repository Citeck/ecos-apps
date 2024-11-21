package ru.citeck.ecos.apps.domain.artifact.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import ru.citeck.ecos.apps.app.api.GetAppBuildInfoCommand
import ru.citeck.ecos.apps.app.api.GetAppBuildInfoCommandResp
import ru.citeck.ecos.apps.app.domain.artifact.source.AppSourceKey
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceInfo
import ru.citeck.ecos.apps.app.domain.handler.ArtifactDeployMeta
import ru.citeck.ecos.apps.app.service.remote.RemoteAppService
import ru.citeck.ecos.apps.app.service.remote.RemoteAppStatus
import ru.citeck.ecos.apps.artifact.ArtifactService
import ru.citeck.ecos.apps.artifact.type.ArtifactTypeService
import ru.citeck.ecos.apps.domain.artifact.artifact.service.DeployError
import ru.citeck.ecos.apps.domain.artifact.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifact.artifact.service.deploy.ArtifactDeployer
import ru.citeck.ecos.apps.domain.artifact.source.service.AppArtifactsSource
import ru.citeck.ecos.apps.domain.artifact.source.service.EcosArtifactsSourcesService
import ru.citeck.ecos.apps.domain.artifact.type.service.EcosArtifactTypesService
import ru.citeck.ecos.apps.domain.devtools.buildinfo.api.records.BuildInfoRecords
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commands.dto.CommandResult
import ru.citeck.ecos.commands.utils.CommandUtils.getTargetAppByAppInstanceId
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.io.file.EcosFile
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

@Service
class EcosApplicationsService(
    private val ecosArtifactsSourcesService: EcosArtifactsSourcesService,
    private val ecosArtifactTypesService: EcosArtifactTypesService,
    private val ecosArtifactsService: EcosArtifactsService,
    private val artifactTypeService: ArtifactTypeService,
    private val remoteAppService: RemoteAppService,
    private val artifactService: ArtifactService,
    private val commandsService: CommandsService,
    private val buildInfoRecords: BuildInfoRecords
) {

    companion object {
        val log = KotlinLogging.logger {}
    }

    private var appsStatusByName: Map<String, List<RemoteAppStatus>> = emptyMap()
    private var buildInfoLastTimeByAppName: MutableMap<String, Instant> = mutableMapOf()

    private val sources = ConcurrentHashMap<AppSourceKey, AppArtifactsSourceImpl>()
    private val typesSources = ConcurrentHashMap<String, RemoteAppStatus>()
    private val deployers = ConcurrentHashMap<String, AppArtifactsDeployer>()

    @Synchronized
    fun updateApps(appsStatus: List<RemoteAppStatus>) {

        val appsByName = HashMap<String, MutableList<RemoteAppStatus>>()

        appsStatus.forEach { appsByName.computeIfAbsent(it.appName) { ArrayList() }.add(it) }

        for ((appName, statuses) in appsByName) {

            val lastStatus = statuses.maxByOrNull { it.status.startTime } ?: continue
            val lastUpdateTime = buildInfoLastTimeByAppName[appName] ?: Instant.EPOCH

            if (lastStatus.status.startTime.isAfter(lastUpdateTime)) {

                buildInfoLastTimeByAppName[appName] = lastStatus.status.startTime

                val appFullName = "$appName (${lastStatus.appInstanceId})"
                try {
                    loadBuildInfo(appFullName, lastStatus)
                } catch (e: Exception) {
                    log.error(e) { "Error in build info request for app $appFullName" }
                }
            }
        }

        val newSources = mutableMapOf<AppSourceKey, AppArtifactsSourceInfo>()
        val typesSourcesByAppName = mutableMapOf<String, RemoteAppStatus>()

        appsByName.forEach { (appName, appStatus) ->

            appStatus.forEach { appInstance ->

                val typesSource = typesSourcesByAppName[appInstance.appName]
                if (typesSource == null ||
                    appInstance.status.typesLastModified.isAfter(typesSource.status.typesLastModified)
                ) {

                    typesSourcesByAppName[appInstance.appName] = appInstance
                }

                appInstance.status.sources.forEach { source ->

                    val appSourceKey = AppSourceKey(appName, source.key)
                    val currentSource = newSources[appSourceKey]

                    if (currentSource == null) {
                        newSources[appSourceKey] = AppArtifactsSourceInfo(appInstance, source)
                    } else if (currentSource.sourceInfo.lastModified.isBefore(source.lastModified)) {
                        newSources[appSourceKey] = AppArtifactsSourceInfo(appInstance, source)
                    }
                }
            }
        }

        val deployersToRemove = mutableListOf<String>()
        deployers.keys.forEach { appName ->
            if (!typesSourcesByAppName.containsKey(appName)) {
                deployersToRemove.add(appName)
            }
        }
        deployersToRemove.forEach { deployers.remove(it) }

        typesSourcesByAppName.values.forEach { appStatus ->

            val currentSource = typesSources[appStatus.appName]

            if (currentSource == null ||
                currentSource.status.typesLastModified.isBefore(appStatus.status.typesLastModified)
            ) {

                try {
                    val typesDir = remoteAppService.getArtifactTypesDir(appStatus.appInstanceId)
                    ecosArtifactTypesService.registerTypes(
                        appStatus.appName,
                        typesDir,
                        appStatus.status.typesLastModified
                    )

                    deployers.remove(appStatus.appName)
                    typesSources[appStatus.appName] = appStatus
                } catch (e: Exception) {
                    log.error(e) { "Types dir loading error. App: $appStatus" }
                }
            }

            if (!deployers.containsKey(appStatus.appName)) {

                val supportedTypesByController = ecosArtifactTypesService.getTypesByAppName(appStatus.appName)
                    .map { type -> type.getId() }
                val supportedTypes = supportedTypesByController.intersect(appStatus.status.supportedTypes).toList()

                if (supportedTypes.isNotEmpty()) {

                    log.info {
                        "Register deployer for ${appStatus.appName} " +
                            "- ${appStatus.appInstanceId} with supported types: $supportedTypes"
                    }

                    deployers[appStatus.appName] = AppArtifactsDeployer(
                        appStatus.appName,
                        appStatus.appInstanceId,
                        supportedTypes,
                        remoteAppService
                    )
                }
            }
        }

        val sourcesToRemove = mutableSetOf<AppSourceKey>()

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
                    .isBefore(appSourceInfo.sourceInfo.lastModified)
            ) {

                val appKey = AppKey(appSourceInfo.appStatus.appName, appSourceInfo.appStatus.appInstanceId)
                val appSource = AppArtifactsSourceImpl(
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

        this.appsStatusByName = appsByName
    }

    private fun loadBuildInfo(appFullName: String, app: RemoteAppStatus) {

        log.info { "Send build info request to $appFullName" }

        val comRes: CommandResult = commandsService.execute(
            commandsService.buildCommand {
                this.targetApp = getTargetAppByAppInstanceId(app.appInstanceId)
                this.body = GetAppBuildInfoCommand(Instant.EPOCH)
            }
        ).get(10, TimeUnit.SECONDS)

        comRes.throwPrimaryErrorIfNotNull()

        val resp = comRes.getResultAs(GetAppBuildInfoCommandResp::class.java)
        if (resp != null) {
            val info = "[" + resp.buildInfo.joinToString { info ->
                "\"name\": \"${MLText.getClosestValue(info.name, null)}\", " +
                    "\"repo\": \"${info.repo}\", " +
                    "\"version\": \"${info.version}\", " +
                    "\"branch\": \"${info.branch}\", " +
                    "\"buildDate\": \"${info.buildDate}\""
            } + "]"
            log.info { "Register new build info for app $appFullName $info" }
            buildInfoRecords.register(app, resp.buildInfo)
        } else {
            log.error { "Build info is null for app $appFullName" }
        }
    }

    fun getAppsStatus(): Map<String, List<RemoteAppStatus>> {
        return appsStatusByName
    }

    fun deployArtifacts(lastModified: Instant) {
        deployers.values.forEach {
            try {
                var iter = -1
                while (++iter < 10) {
                    if (!ecosArtifactsService.deployArtifacts(it, lastModified)) {
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

        override fun deploy(type: String, artifact: ByteArray, meta: ArtifactDeployMeta): List<DeployError> {

            val commandErrors = remoteAppService.deployArtifact(instanceId, type, artifact, meta)
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

    private class AppArtifactsSourceImpl(
        val appKey: AppKey,
        val sourceInfo: ArtifactSourceInfo,
        val remoteAppService: RemoteAppService,
        val artifactsService: ArtifactService,
        val artifactTypesService: ArtifactTypeService
    ) : AppArtifactsSource {

        override fun getArtifacts(typesDir: EcosFile, since: Instant): Map<String, List<Any>> {
            val artifactsDir = try {
                remoteAppService.getArtifactsDir(appKey.instanceId, sourceInfo.key, typesDir, since)
            } catch (e: Throwable) {
                throw RuntimeException(
                    "remoteAppService.getArtifactsDir failed " +
                        "for app $appKey and sourceInfo $sourceInfo",
                    e
                )
            }
            return artifactsService.readArtifacts(artifactsDir, artifactTypesService.loadTypes(typesDir))
        }

        override fun getLastModified(): Instant {
            return sourceInfo.lastModified
        }

        override fun getKey(): AppSourceKey {
            return AppSourceKey(appKey.appName, sourceInfo.key)
        }
    }

    private data class AppKey(
        val appName: String,
        val instanceId: String
    )
}
