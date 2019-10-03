package ru.citeck.ecos.apps.app.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import ru.citeck.ecos.apps.app.AppUtils;
import ru.citeck.ecos.apps.app.AppVersion;
import ru.citeck.ecos.apps.app.module.EcosModuleTypesFactory;
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
public class EcosAppParser {

    private ObjectMapper mapper = new ObjectMapper();
    private List<ModuleReader> moduleReaders;

    public EcosAppParser(EcosModuleTypesFactory typesFactory) {
        this.moduleReaders = typesFactory.getModuleReaders();
    }

    public EcosApp parseData(byte[] data) {

        File rootDir = AppUtils.getTmpDirToExtractApp();
        try {
            AppUtils.extractZip(data, rootDir);
            return loadAppFromFolder(rootDir);
        } catch (Exception e) {
            FileSystemUtils.deleteRecursively(rootDir);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
    }

    private EcosApp loadAppFromFolder(File rootDir) throws Exception {

        File metaFile = new File(rootDir, "meta.json");
        EcosAppDto dto = mapper.readValue(metaFile, EcosAppDto.class);

        File modulesRoot = new File(rootDir, "modules");
        List<EcosModule> modules = new ArrayList<>();

        for (ModuleReader reader : moduleReaders) {
            for (String pattern : reader.getFilePatterns()) {

                List<ModuleFile> moduleFiles = getFiles(modulesRoot, pattern);
                if (!moduleFiles.isEmpty()) {
                    modules.addAll(reader.read(pattern, moduleFiles));
                }
            }
        }

        return new EcosAppImpl(rootDir, dto, modules);
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
        public ModuleFile getRelative(String path) {
            return new ModuleFileImpl(this.path.getParent().resolve(path));
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
        private EcosAppDto dto;
        private List<EcosModule> modules;

        private List<Dependency> dependencies = new ArrayList<>();

        public EcosAppImpl(File rootDir,
                           EcosAppDto dto,
                           List<EcosModule> modules) {

            this.modules = modules;
            this.rootDir = rootDir;
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
        public void close() {
            try {
                FileSystemUtils.deleteRecursively(rootDir);
            } catch (Exception e) {
                log.warn("Temp files can't be deleted", e);
            }
        }
    }
}
