package ru.citeck.ecos.apps.app.module;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.AppUtils;
import ru.citeck.ecos.apps.app.Digest;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;
import ru.citeck.ecos.apps.module.type.EcosModule;
import ru.citeck.ecos.apps.repository.EcosModuleRevRepo;
import ru.citeck.ecos.apps.repository.EcosModuleRepo;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class EcosModuleDao {

    private EcosModuleRepo modulesRepo;
    private EcosModuleRevRepo moduleRevRepo;

    public EcosModuleDao(EcosModuleRepo modulesRepo,
                         EcosModuleRevRepo moduleRevRepo) {
        this.modulesRepo = modulesRepo;
        this.moduleRevRepo = moduleRevRepo;
    }

    public EcosModuleRevEntity getLastModuleRev(String type, String id) {
        Pageable page = PageRequest.of(0, 1);
        List<EcosModuleRevEntity> result = moduleRevRepo.getModuleRevisions(type, id, page);
        return result.stream().findFirst().orElse(null);
    }

    public EcosModuleRevEntity getModuleRev(String revId) {
        return moduleRevRepo.getRevByExtId(revId);
    }

    public void save(EcosModuleRevEntity entity) {
        moduleRevRepo.save(entity);
    }

    public List<EcosModuleRevEntity> uploadModules(List<EcosModule> modules) {

        List<EcosModuleRevEntity> result = new ArrayList<>();

        for (EcosModule module : modules) {

            log.info("Try to upload module " + module.getId());

            EcosModuleEntity moduleEntity = modulesRepo.getByExtId(module.getId());
            if (moduleEntity == null) {
                moduleEntity = new EcosModuleEntity();
                moduleEntity.setExtId(module.getId());
                moduleEntity.setType(module.getType());
                moduleEntity = modulesRepo.save(moduleEntity);
            }

            EcosModuleRevEntity uploadRev = moduleEntity.getUploadRev();
            byte[] data = module.getData();
            Digest digest = AppUtils.getDigest(new ByteArrayInputStream(data));

            if (uploadRev == null
                || uploadRev.getSize() != data.length
                || !digest.getHash().equals(uploadRev.getHash())) {

                uploadRev = new EcosModuleRevEntity();
                uploadRev.setData(data);
                uploadRev.setSize(digest.getSize());
                uploadRev.setHash(digest.getHash());
                uploadRev.setExtId(UUID.randomUUID().toString());
                uploadRev.setDataType(module.getDataType());
                uploadRev.setName(module.getName());
                uploadRev.setModule(moduleEntity);
                uploadRev.setModelVersion(module.getModelVersion());

                uploadRev = moduleRevRepo.save(uploadRev);
                moduleEntity.setUploadRev(uploadRev);
                modulesRepo.save(moduleEntity);

                log.info("Module uploaded: " + module.getId());

            } else {
                log.info("Module already uploaded: " + module.getId());
            }

            result.add(uploadRev);
        }

        return result;
    }

    public List<EcosModule> getModulesByAppRev() {
        return Collections.emptyList();
    }
}
