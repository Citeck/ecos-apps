package ru.citeck.ecos.apps.domain.devtools.devmodule.eapps

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.apps.domain.devtools.devmodule.dto.DevModuleDef
import ru.citeck.ecos.apps.domain.devtools.devmodule.service.DevModulesService
import java.util.function.Consumer

@Component
class DevModuleArtifactHandler(val service: DevModulesService) : EcosArtifactHandler<DevModuleDef> {

    override fun deployArtifact(artifact: DevModuleDef) {
        service.save(artifact)
    }

    override fun getArtifactType(): String {
        return "app/dev-module"
    }

    override fun listenChanges(listener: Consumer<DevModuleDef>) {
    }

    override fun deleteArtifact(artifactId: String) {
        service.delete(artifactId)
    }
}
