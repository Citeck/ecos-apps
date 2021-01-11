package ru.citeck.ecos.apps.domain.deployer.service.loader

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.artifact.type.TypeContext
import ru.citeck.ecos.apps.domain.application.service.EcosArtifactTypesService
import ru.citeck.ecos.apps.domain.artifact.service.EcosArtifactsService
import ru.citeck.ecos.apps.domain.artifactpatch.service.ArtifactPatchService
import ru.citeck.ecos.apps.domain.deployer.service.consumer.ArtifactsConsumer
import java.util.concurrent.ConcurrentHashMap

@Component
class ArtifactsLoader(
    private val ecosArtifactsService: EcosArtifactsService,
    private val artifactsPatchService: ArtifactPatchService,
    private val ecosArtifactTypesService: EcosArtifactTypesService
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val sources = ConcurrentHashMap<String, ArtifactsSource>()

    @Synchronized
    fun addSource(source: ArtifactsSource): Set<Pair<String, String>> {

        sources[source.getId()] = source

        return loadArtifacts(source.getArtifacts(ecosArtifactTypesService.allTypesCtx))
    }

    @Synchronized
    fun removeSource(sourceId: String) {
        sources.remove(sourceId)
    }

    @Synchronized
    fun load(types: List<TypeContext>): Set<Pair<String, String>> {

        val artifacts = mutableMapOf<String, MutableList<Any>>()
        sources.values.forEach { it.getArtifacts(types).forEach {
            (typeId, artifactsList) ->
                artifacts.getOrPut(typeId) { ArrayList() }.addAll(artifactsList)
            }
        }

        return loadArtifacts(artifacts)
    }

    private fun loadArtifacts(artifacts: Map<String, List<Any>>): Set<Pair<String, String>> {



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
                                consumer: ArtifactsConsumer) {


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
                    val ref = ArtifactRef.create(type.getId(), it.meta.id)
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
