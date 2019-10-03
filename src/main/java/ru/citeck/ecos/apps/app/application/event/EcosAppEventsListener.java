package ru.citeck.ecos.apps.app.application.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.citeck.ecos.apps.app.application.EcosAppDao;
import ru.citeck.ecos.apps.app.module.EcosModuleService;
import ru.citeck.ecos.apps.app.module.event.ModuleRevisionCreated;
import ru.citeck.ecos.apps.app.module.event.ModuleStatusChanged;

@Component
public class EcosAppEventsListener {

    private final EcosAppDao ecosAppDao;
    private final EcosModuleService moduleService;

    public EcosAppEventsListener(EcosAppDao ecosAppDao, EcosModuleService moduleService) {
        this.ecosAppDao = ecosAppDao;
        this.moduleService = moduleService;
    }

    @EventListener
    public void onModuleStatusChanged(ModuleStatusChanged event) {
        ecosAppDao.updatePublishStatus(event.getModule());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onModuleRevisionCreated(ModuleRevisionCreated event) {
        moduleService.publishModule(event.getType(), event.getId());
    }
}
