package eapps.types.app.ecosapp

import kotlin.Unit
import org.jetbrains.annotations.NotNull
import ru.citeck.ecos.apps.artifact.ArtifactMeta
import ru.citeck.ecos.apps.artifact.controller.ArtifactController
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.data.Version
import ru.citeck.ecos.commons.io.file.EcosFile
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.commons.utils.ZipUtils

import java.util.function.Function
import java.util.function.Predicate
import java.util.stream.Collectors

class EcosAppControllerImpl implements ArtifactController<EcosAppArtifact, Unit> {

    @Override
    List<EcosAppArtifact> read(@NotNull EcosFile root, Unit config) {

        return root.getChildren()
            .stream()
            .map(new Function<EcosFile, Optional<EcosAppArtifact>>() {
                @Override
                Optional<EcosAppArtifact> apply(EcosFile ecosAppDir) {
                    return Optional.ofNullable(readArtifact(ecosAppDir))
                }
            }).filter(new Predicate<Optional<EcosAppArtifact>>() {
                @Override
                boolean test(Optional<EcosAppArtifact> artifact) {
                    return artifact.isPresent()
                }
            }).map(new Function<Optional<EcosAppArtifact>, EcosAppArtifact>() {
                @Override
                EcosAppArtifact apply(Optional<EcosAppArtifact> artifact) {
                    return artifact.get()
                }
            }).collect(Collectors.toList())
    }

    private static EcosAppArtifact readArtifact(EcosFile ecosApp) {

        def ecosAppDir = ecosApp
        if (ecosApp.name.endsWith(".zip")) {
            ecosAppDir = ecosApp.read( {input -> ZipUtils.extractZip(input) })
        }

        def metaFile = ecosAppDir.getFile("meta.yml")
        if (metaFile == null) {
            metaFile = ecosAppDir.getFile("meta.yaml")
        }
        if (metaFile == null) {
            metaFile = ecosAppDir.getFile("meta.json")
        }
        def meta = Json.mapper.read(metaFile, ObjectData.class)
        if (meta == null) {
            return null
        }

        def id = meta.get("id").asText()
        if (id.isEmpty()) {
            return null
        }

        def artifactsDir = ecosAppDir.getDir("artifacts")
        if (artifactsDir == null) {
            return null
        }

        def artifact = new EcosAppArtifact()
        artifact.setId(id)
        artifact.setName(meta.get("name", MLText.class))
        artifact.setArtifactsDir(ZipUtils.writeZipAsBytes(artifactsDir))
        artifact.setMetaContent(metaFile.readAsBytes())

        String versionStr = meta.get("version").asText()
        if (versionStr.isEmpty()) {
            versionStr = "1.0"
        }
        artifact.version = Version.valueOf(versionStr)

        return artifact
    }

    @Override
    void write(@NotNull EcosFile root, EcosAppArtifact artifact, Unit config) {

        def appDir = root.createDir(artifact.id)

        appDir.createFile("meta.json", artifact.metaContent)

        def artifactsTarget = appDir.createDir("artifacts")
        def artifactsImMem = ZipUtils.extractZip(artifact.artifactsDir)
        artifactsTarget.copyFilesFrom(artifactsImMem)
    }

    @Override
    ArtifactMeta getMeta(@NotNull EcosAppArtifact artifact, @NotNull Unit unit) {
        return ArtifactMeta.create()
            .withId(artifact.id)
            .withName(artifact.name)
            .withVersion(artifact.version)
            .build()
    }
}

class EcosAppArtifact {

    String id
    MLText name
    Version version

    byte[] metaContent
    byte[] artifactsDir
}


return new EcosAppControllerImpl()
