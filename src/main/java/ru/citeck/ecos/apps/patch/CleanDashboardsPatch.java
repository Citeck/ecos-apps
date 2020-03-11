package ru.citeck.ecos.apps.patch;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.EcosModuleDao;
import ru.citeck.ecos.apps.domain.EcosModuleEntity;
import ru.citeck.ecos.metarepo.EcosMetaRepo;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@Component
@DependsOn("liquibase")
@RequiredArgsConstructor
public class CleanDashboardsPatch {

    private static final String PATCH_KEY = "patch.CleanDashboardsPatch_v5";

    private final EcosMetaRepo metaRepo;
    private final EcosModuleDao modulesDao;

    @PostConstruct
    public void execute() {

        PatchState state = metaRepo.get(PATCH_KEY, PatchState.class);
        if (state != null) {
            return;
        }

        state = new PatchState();

        List<EcosModuleEntity> modules = modulesDao.getModulesByType("ui/dashboard");
        state.total = modules.size();

        modules.forEach(modulesDao::delete);

        metaRepo.put(PATCH_KEY, state);
    }

    @Data
    public static class PatchState {
        private int total;
    }
}


