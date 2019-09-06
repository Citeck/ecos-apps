package ru.citeck.ecos.apps.app.module;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.apps.app.AppUtils;
import ru.citeck.ecos.apps.app.Digest;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;
import ru.citeck.ecos.apps.repository.EcosAppModuleRevRepo;
import ru.citeck.ecos.apps.repository.EcosAppModuleRepo;

import javax.sql.rowset.serial.SerialBlob;
import java.io.ByteArrayInputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Slf4j
@Service
public class EcosModuleDao {

    private EcosAppModuleRepo modulesRepo;
    private EcosAppModuleRevRepo moduleRevRepo;

    public EcosModuleDao(EcosAppModuleRepo modulesRepo,
                         EcosAppModuleRevRepo moduleRevRepo) {
        this.modulesRepo = modulesRepo;
        this.moduleRevRepo = moduleRevRepo;
    }

    /*public EcosModuleRev getModuleById(String id) {

        EcosAppModuleEntity moduleById = modulesRepo.getByExtId(id);
        EcosAppModuleRevEntity revision = moduleRevRepo.getLastRevisionByAppId(moduleById.getId());

        EcosModuleImpl module = new EcosModuleImpl();
        module.setId(id);
        module.setRevId(revision.getExtId());
        module.setData(revision.getData());
        module.setName(revision.getName());
        module.setModelVersion(revision.getModelVersion());
        module.setMimetype(revision.getMimetype());
        module.setType(moduleById.getType());

        return module;
    }*/

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
                try {
                    uploadRev.setData(new SerialBlob(data));
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }

                uploadRev.setSize(digest.getSize());
                uploadRev.setHash(digest.getHash());
                uploadRev.setExtId(module.getId());
                uploadRev.setMimetype(module.getMimetype());
                uploadRev.setName(module.getName());
                uploadRev.setModule(moduleEntity);
                uploadRev.setModelVersion(module.getModelVersion());

                uploadRev = moduleRevRepo.save(uploadRev);
                moduleEntity.setUploadRev(uploadRev);
                modulesRepo.save(moduleEntity);

                log.info("Module uploaded " + module.getId());

            } else {
                log.info("Module already uploaded " + module.getId());
            }

            result.add(uploadRev);
        }

        return result;
    }

    public List<EcosModule> getModulesByAppRev() {
        return Collections.emptyList();
    }
}
