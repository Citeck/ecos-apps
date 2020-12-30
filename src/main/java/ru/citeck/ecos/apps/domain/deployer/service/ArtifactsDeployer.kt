package ru.citeck.ecos.apps.domain.deployer.service

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.domain.artifact.service.ArtifactsService
import ru.citeck.ecos.apps.domain.artifactpatch.service.ArtifactPatchService
import ru.citeck.ecos.apps.module.ModuleRef
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@Component
class ArtifactsDeployer(
    val artifactsService: ArtifactsService,
    val artifactsPatchService: ArtifactPatchService
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val sources = ConcurrentHashMap<String, ArtifactsSource>()
    private val consumers = ConcurrentHashMap<String, ArtifactsConsumer>()

    private val newSourcesQueue = ConcurrentLinkedQueue<String>()
    private val newConsumersQueue = ConcurrentLinkedQueue<String>()

    private var lastChangedMs = 0L

    private var currentChangedVersion = 0
    private var syncChangedVersion = 0

    @Synchronized
    fun addConsumer(consumer: ArtifactsConsumer) {
        consumers[consumer.getId()] = consumer
        wasChanged()
    }

    @Synchronized
    fun addSource(source: ArtifactsSource) {
        sources[source.getId()] = source
        wasChanged()
    }

    @Synchronized
    fun removeSource(sourceId: String) {
        sources.remove(sourceId)
        // additional sync (wasChanged) doesn't required when source was removed
    }

    @Synchronized
    fun removeConsumer(consumerId: String) {
        consumers.remove(consumerId)
        // additional sync (wasChanged) doesn't required when consumer was removed
    }

    private fun wasChanged() {
        currentChangedVersion++
        lastChangedMs = System.currentTimeMillis()
    }

    fun getLastChangedMs(): Long {
        return lastChangedMs
    }

    @Synchronized
    fun syncNewSourcesAndConsumers() {

        if (syncChangedVersion == currentChangedVersion) {
            log.debug { "Sync will be skipped because nothing changed since last sync. Version: $syncChangedVersion" }
            return
        }

        log.info { "Sync new sources (${newSourcesQueue.size}) and consumers (${newConsumersQueue.size})" }

        val sourcesToRemove = HashSet<String>()
        val consumersToRemove = HashSet<String>()

        val newSources = HashSet<String>()
        var sourceId: String? = newSourcesQueue.poll()
        while (sourceId != null) {
            if (newSources.add(sourceId)) {
                val source = sources[sourceId]
                if (source != null) {
                    consumers.values.forEach {
                        deployArtifacts(source, it, sourcesToRemove, consumersToRemove)
                    }
                }
            }
            sourceId = newSourcesQueue.poll()
        }

        var consumerId: String? = newConsumersQueue.poll()
        while (consumerId != null) {
            val consumer = consumers[consumerId]
            if (consumer != null) {
                sources.values.filter {
                    !newSources.contains(it.getId())
                }.forEach {
                    deployArtifacts(it, consumer, sourcesToRemove, consumersToRemove)
                }
            }
            consumerId = newConsumersQueue.poll()
        }

        sourcesToRemove.forEach { removeSource(it) }
        consumersToRemove.forEach { removeConsumer(it) }

        syncChangedVersion = currentChangedVersion
    }

    private fun deployArtifacts(source: ArtifactsSource,
                                consumer: ArtifactsConsumer,
                                sourcesToRemove: MutableSet<String>,
                                consumersToRemove: MutableSet<String>) {

        if (!source.isValid() || !consumer.isValid()) {
            if (!source.isValid()) {
                sourcesToRemove.add(source.getId())
            }
            if (!consumer.isValid()) {
                consumersToRemove.add(consumer.getId())
            }
            return
        }

        val types = consumer.getConsumedTypes()
        if (types.isEmpty()) {
            return
        }

        val artifacts = source.getArtifacts(types)
        if (artifacts.isEmpty()) {
            return
        }

        types.forEach { type ->

            val typeArtifacts = artifacts[type.getId()]

            if (typeArtifacts != null && typeArtifacts.isNotEmpty()) {

                val artifactsToDeployBeforePatch = consumer.prepareToDeploy(type, typeArtifacts)
                val patchedArtifacts = ArrayList<Any>()
                val artifactsToDeploy = ArrayList<ModuleWithMeta<Any>>()

                artifactsToDeployBeforePatch.forEach {
                    val ref = ModuleRef.create(type.getId(), it.meta.id)
                    val patches = artifactsPatchService.getPatches(ref)
                    if (patches.isEmpty()) {
                        artifactsToDeploy.add(it)
                    } else {
                        patchedArtifacts.add(artifactsPatchService.applyPatches(it.module, ref, patches))
                    }
                }



                artifactsToDeploy.addAll(consumer.prepareToDeploy(type, patchedArtifacts))



                log.info { "Deploy artifacts from (${newSourcesQueue.size}) and consumers (${newConsumersQueue.size})" }

                consumer.deployArtifacts(artifacts)
            }
        }
    }
}
