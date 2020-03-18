package ru.citeck.ecos.apps.patch;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.EcosModuleDao;
import ru.citeck.ecos.apps.domain.EcosContentEntity;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;
import ru.citeck.ecos.apps.domain.EcosModuleRevEntity;
import ru.citeck.ecos.metarepo.EcosMetaRepo;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@DependsOn("liquibase")
@RequiredArgsConstructor
public class UpdateModulesKeysPatch {

    /*private static final String PATCH_KEY = "patch.UpdateModulesKeys_v2";

    private final EcosMetaRepo metaRepo;
    private final EcosModuleDao modulesDao;
    private final EappsModuleService eappsModuleService;

    private boolean executed = false;

    @PostConstruct
    public void onInit() {

        if (executed) {
            return;
        }

        ExecState state = metaRepo.get(PATCH_KEY, ExecState.class);

        if (state != null) {
            return;
        }

        state = new ExecState();

        List<EcosModuleEntity> modules = modulesDao.getAllModules();
        state.setTotal(modules.size());

        int updated = 0;
        int errors = 0;

        for (EcosModuleEntity entity : modules) {

            String type = entity.getType();

            try {

                EcosModuleRevEntity lastRev = entity.getLastRev();

                if (lastRev == null) {

                    log.warn("module doesn't have last revision: " + type + " "
                        + entity.getId() + " " + entity.getKey());

                } else {

                    EcosContentEntity contentEntity = lastRev.getContent();

                    if (contentEntity == null) {

                        log.warn("Module doesn't have content: "  + type + " "
                            + entity.getId() + " " + entity.getKey());

                    } else {

                        byte[] content = contentEntity.getData();

                        EcosModule module = eappsModuleService.read(content, type);

                        String newKey = eappsModuleService.getModuleKey(module);
                        String oldKey = entity.getKey();

                        if (!Objects.equals(newKey, oldKey)) {

                            entity.setKey(eappsModuleService.getModuleKey(module));
                            modulesDao.save(entity);

                            state.getDiffs().add(new Diff(entity.getId(), oldKey, newKey));

                            updated++;
                        }
                    }
                }

            } catch (Exception e) {
                String addInfo = "Type: '" + type + "' and entity '" + entity.getId() + "' '" + entity.getExtId() + "'";
                state.getErrors().add(e.getMessage() + " " + addInfo);
                log.error("Error! " + addInfo, e);
                errors++;
            }
        }

        state.setErrorsCount(errors);
        state.setUpdated(updated);
        state.setEnd(Instant.now());

        metaRepo.put(PATCH_KEY, state);
        executed = true;
    }

    @Data
    public static final class ExecState {

        private Instant start = Instant.now();
        private Instant end;

        private int updated;
        private int errorsCount;
        private int total;

        private List<Diff> diffs = new ArrayList<>();
        private List<String> errors = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static final class Diff {

        private long id;
        private String before;
        private String after;
    }*/
}
