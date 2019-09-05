package ru.citeck.ecos.apps.app.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import ru.citeck.ecos.apps.app.AppUtils;
import ru.citeck.ecos.apps.app.Digest;
import ru.citeck.ecos.apps.app.module.Dependency;
import ru.citeck.ecos.apps.app.module.EcosModule;
import ru.citeck.ecos.apps.app.module.type.ModuleFile;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class EcosAppReader {

    private ResourceLoader resourceLoader;
    private ObjectMapper mapper = new ObjectMapper();

    public EcosAppReader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public EcosApp load(String location) {
        return load(resourceLoader.getResource(location));
    }

    /**
     * @return application or null if resource is not a valid application
     */
    public EcosApp load(Resource resource) {

        String uri = "";
        try {
            uri = resource.getURI().toString();
        } catch (IOException e) {
            //do nothing
        }

        log.info("Try to upload application. URI: '" + uri + "'");

        return null;
    }

    private EcosApp loadImpl(Resource resource) {

        File rootDir = AppUtils.getTmpDirToExtractApp();
        EcosAppImpl app;

        try {

            try (InputStream in = resource.getInputStream()) {
                AppUtils.extractZip(in, rootDir);
            }

            Digest digest;
            try (InputStream in = resource.getInputStream()) {
                digest = AppUtils.getDigest(in);
            }

            File metaFile = new File(rootDir, "meta.json");
            EcosAppDto dto = mapper.readValue(metaFile, EcosAppDto.class);


            rootDir.

            app = new EcosAppImpl(rootDir, digest, dto);

        } catch (Exception e) {
            FileSystemUtils.deleteRecursively(rootDir);
            throw new RuntimeException(e);
        }
        return null;
    }

    private List<ModuleFile> getFiles(File root, String glob) throws IOException {

        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(glob);

        List<Path> filePaths = new ArrayList<>();

        Files.walkFileTree(Paths.get(root.getAbsolutePath()), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                if (pathMatcher.matches(path)) {
                    filePaths.add(path);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        filePaths.stream().map(p -> {



        });
    }

    private File

    @Data
    public static class EcosAppDto {
        private String id;
        private String name;
        private String version;
        private Map<String, String> dependencies = Collections.emptyMap();
    }

    @Data
    @AllArgsConstructor
    private static class DependencyImpl implements Dependency {
        private String id;
        private String version;
    }

    private static class EcosAppImpl implements EcosApp {

        private File rootDir;
        private Digest digest;
        private EcosAppDto dto;

        private List<Dependency> dependencies = new ArrayList<>();

        public EcosAppImpl(File rootDir, Digest digest, EcosAppDto dto) {
            this.rootDir = rootDir;
            this.digest = digest;
            this.dto = dto;
            dto.getDependencies().forEach((d, v) -> dependencies.add(new DependencyImpl(d, v)));
        }

        @Override
        public String getId() {
            return dto.getId();
        }

        @Override
        public String getName() {
            return dto.getName();
        }

        @Override
        public String getVersion() {
            return dto.getVersion();
        }

        @Override
        public List<Dependency> getDependencies() {
            return dependencies;
        }

        @Override
        public List<EcosModule> getModules() {
            return null;
        }

        @Override
        public String getHash() {
            return digest.getHash();
        }

        @Override
        public long getSize() {
            return digest.getSize();
        }

        @Override
        public void dispose() {
            try {
                FileSystemUtils.deleteRecursively(rootDir);
            } catch (Exception e) {
                log.warn("Temp files can't be deleted", e);
            }
        }
    }
}
