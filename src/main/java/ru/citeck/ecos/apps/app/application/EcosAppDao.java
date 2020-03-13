package ru.citeck.ecos.apps.app.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.EcosModuleDao;
import ru.citeck.ecos.apps.repository.EcosAppRepo;
import ru.citeck.ecos.apps.repository.EcosAppRevRepo;

@Slf4j
@Component
public class EcosAppDao {

    //private EcosAppIO ecosAppIO;

    private EcosAppRepo appRepo;
    private EcosAppRevRepo appRevRepo;
    private EcosModuleDao moduleDao;
/*
    public EcosAppDao(EcosAppIO ecosAppIO,
                      EcosAppRepo appRepo,
                      EcosAppRevRepo appRevRepo,
                      EcosModuleDao moduleDao) {

        this.ecosAppIO = ecosAppIO;
        this.appRepo = appRepo;
        this.appRevRepo = appRevRepo;
        this.moduleDao = moduleDao;
    }

    public UploadStatus<EcosAppRevEntity> uploadApp(String source, byte[] data) {

        EcosApp app = ecosAppIO.read(data);

        log.info("Start application uploading: " + app.getName()
            + " (" + app.getId() + "). Source: " + source
            + " Modules: " + app.getModules().size()
            + " Patches: " + app.getPatches().size());

        if (app.getModules().isEmpty() && app.getPatches().isEmpty()) {
            throw new IllegalArgumentException("Empty application");
        }

        EcosAppEntity appEntity = appRepo.getByExtId(app.getId());
        EcosAppRevEntity appLastRev = null;

        if (appEntity == null) {

            appEntity = new EcosAppEntity();
            appEntity.setExtId(app.getId());

        } else {

            appLastRev = getLastRevisionByAppId(appEntity.getId());
        }

        String currVersionStr = appEntity.getVersion();
        String currentVersionStr = StringUtils.isNotBlank(currVersionStr) ? currVersionStr : "0";
        EcosAppVersion currentVersion = new EcosAppVersion(currentVersionStr);

        if (appEntity.getId() == null || !currentVersion.equals(app.getVersion())) {
            appEntity.setVersion(app.getVersion().toString());
            appEntity = appRepo.save(appEntity);
        }

        Set<EcosModuleRevEntity> currentModules = Collections.emptySet();
        if (appLastRev != null) {
            currentModules = new HashSet<>(appLastRev.getModules());
        }

        Set<EcosModuleRevEntity> uploadedModules = app.getModules()
            .stream()
            .map(m -> moduleDao.uploadModule(source, m).getEntity())
            .collect(Collectors.toSet());

        boolean isAppChanged = !currentModules.equals(uploadedModules);

        if (isAppChanged) {

            appLastRev = new EcosAppRevEntity();
            appLastRev.setApplication(appEntity);
            appLastRev.setModules(new HashSet<>(uploadedModules));
            appLastRev.setName(app.getName());
            appLastRev.setExtId(UUID.randomUUID().toString());
            appLastRev.setVersion(app.getVersion().toString());
            appLastRev.setSource(source);
            appLastRev.setDependencies(getDependencies(appLastRev, app.getDependencies()));

            appLastRev = appRevRepo.save(appLastRev);

            log.info("Create new application revision: " + app.getName() + " (" + app.getId() + ")");

        } else {

            log.info("Application doesn't changed: " + app.getName() + " (" + app.getId() + ")");
        }

        log.info("Application uploading finished: " + app.getName() + " (" + app.getId() + ")");

        return new UploadStatus<>(appLastRev, isAppChanged);
    }

    private Set<EcosAppRevDepEntity> getDependencies(EcosAppRevEntity source, Map<String, String> dependencies) {

        if (dependencies == null || dependencies.isEmpty()) {
            return Collections.emptySet();
        }

        Set<EcosAppRevDepEntity> entityDeps = new HashSet<>();

        dependencies.forEach((id, version) -> {

            EcosAppEntity dependencyAppEntity = appRepo.getByExtId(id);

            if (dependencyAppEntity == null) {
                dependencyAppEntity = new EcosAppEntity();
                dependencyAppEntity.setExtId(id);
                dependencyAppEntity.setVersion("0");
                appRepo.save(dependencyAppEntity);
            }

            EcosAppRevDepEntity depEntity = new EcosAppRevDepEntity();
            depEntity.setSource(source);
            depEntity.setVersion(version);
            depEntity.setTarget(dependencyAppEntity);

            entityDeps.add(depEntity);
        });

        return entityDeps;
    }

    public EcosAppEntity save(EcosAppEntity appEntity) {
        return appRepo.save(appEntity);
    }

    public EcosAppEntity getEcosApp(String extId) {
        return appRepo.getByExtId(extId);
    }

    public EcosAppRevEntity getRevByExtId(String extId) {
        return appRevRepo.getByExtId(extId);
    }

    public EcosAppRevEntity getLastRevisionByExtId(String appExtId) {
        List<EcosAppRevEntity> revisions = appRevRepo.getAppRevisions(appExtId, PageRequest.of(0, 1));
        return revisions.stream().findFirst().orElse(null);
    }

    public EcosAppRevEntity getLastRevisionByAppId(long appId) {
        List<EcosAppRevEntity> revisions = appRevRepo.getAppRevisions(appId, PageRequest.of(0, 1));
        return revisions.stream().findFirst().orElse(null);
    }

    public List<EcosAppRevEntity> getAppsRevByModuleRev(PublishStatus status, String revId, Pageable page) {
        return appRevRepo.getAppsByModuleRev(status, revId, page);
    }

 */
}
