package ru.citeck.ecos.apps.app.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import ru.citeck.ecos.apps.app.AppUtils;
import ru.citeck.ecos.apps.app.Digest;
import ru.citeck.ecos.apps.app.EcosModuleTypesFactory;
import ru.citeck.ecos.apps.module.type.EcosModule;
import ru.citeck.ecos.apps.module.type.ModuleFile;
import ru.citeck.ecos.apps.module.type.ModuleReader;
import ru.citeck.ecos.apps.module.type.StreamConsumer;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EcosAppReader {

    private ResourceLoader resourceLoader;
    private ObjectMapper mapper = new ObjectMapper();
    private List<ModuleReader> moduleReaders;

    public EcosAppReader(ResourceLoader resourceLoader,
                         EcosModuleTypesFactory typesFactory) {
        this.resourceLoader = resourceLoader;
        this.moduleReaders = typesFactory.getModuleReaders();
    }

    public EcosApp read(String location) {
        return read(resourceLoader.getResource(location));
    }

    /**
     * @return application or null if resource is not a valid application
     */
    public EcosApp read(Resource resource) {

        String uri = "";
        try {
            uri = resource.getURI().toString();
        } catch (IOException e) {
            //do nothing
        }

        log.info("Try to read application. URI: '" + uri + "'");

        return readApplication(resource);
    }

    private EcosApp readApplication(Resource resource) {

        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();

        try (InputStream in = resource.getInputStream()) {
            IOUtils.copy(in, bytesOut);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return readFromReusableResource(new ByteArrayResource(bytesOut.toByteArray()));
    }

    private EcosApp readFromReusableResource(Resource resource) {

        File rootDir = AppUtils.getTmpDirToExtractApp();
        try {
            Digest digest;
            try (InputStream in = resource.getInputStream()) {
                digest = AppUtils.getDigest(in);
            }
            try (InputStream in = resource.getInputStream()) {
                AppUtils.extractZip(in, rootDir);
            }
            return loadAppFromFolder(rootDir, digest);
        } catch (Exception e) {
            FileSystemUtils.deleteRecursively(rootDir);
            throw new RuntimeException(e);
        }
    }

    private EcosApp loadAppFromFolder(File rootDir, Digest digest) throws Exception {

        File metaFile = new File(rootDir, "meta.json");
        EcosAppDto dto = mapper.readValue(metaFile, EcosAppDto.class);

        File modulesRoot = new File(rootDir, "modules");
        List<EcosModule> modules = new ArrayList<>();

        for (ModuleReader reader : moduleReaders) {
            for (String pattern : reader.getFilePatterns()) {

                List<ModuleFile> moduleFiles = getFiles(modulesRoot, pattern);
                for (ModuleFile mFile : moduleFiles) {
                    modules.addAll(reader.read(pattern, mFile));
                }
            }
        }

        if (modules.isEmpty()) {
            throw new RuntimeException("Application without modules");
        }

        return new EcosAppImpl(rootDir, digest, dto, modules);
    }

    private List<ModuleFile> getFiles(File root, String pattern) {
        return FileUtils.findFiles(root, pattern)
                        .stream()
                        .map(ModuleFileImpl::new)
                        .collect(Collectors.toList());
    }

    private class ModuleFileImpl implements ModuleFile {

        private Path path;

        ModuleFileImpl(Path path) {
            this.path = path;
        }

        @Override
        public <T> T read(StreamConsumer<T> consumer) {
            File file = path.toFile();
            try (InputStream in = new FileInputStream(file)) {
                return consumer.accept(in);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String getName() {
            return path.getFileName().toString();
        }
    }

    @Data
    public static class EcosAppDto {
        private String id;
        private String name;
        private AppVersion version;
        private Map<String, String> dependencies = Collections.emptyMap();
    }

    @Data
    @AllArgsConstructor
    private static class DependencyImpl implements Dependency {
        private String id;
        private String version;
    }

    private static class EcosAppImpl implements EcosApp, Closeable {

        private File rootDir;
        private Digest digest;
        private EcosAppDto dto;
        private List<EcosModule> modules;

        private List<Dependency> dependencies = new ArrayList<>();

        public EcosAppImpl(File rootDir, Digest digest, EcosAppDto dto, List<EcosModule> modules) {
            this.modules = modules;
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
        public AppVersion getVersion() {
            return dto.getVersion();
        }

        @Override
        public List<Dependency> getDependencies() {
            return dependencies;
        }

        @Override
        public List<EcosModule> getModules() {
            return modules;
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
        public void close() {
            try {
                FileSystemUtils.deleteRecursively(rootDir);
            } catch (Exception e) {
                log.warn("Temp files can't be deleted", e);
            }
        }
    }
}
