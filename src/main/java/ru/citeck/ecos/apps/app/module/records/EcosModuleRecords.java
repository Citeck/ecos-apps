package ru.citeck.ecos.apps.app.module.records;

import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.EappsModuleService;
import ru.citeck.ecos.apps.app.module.EcosModule;
import ru.citeck.ecos.apps.app.module.EcosModuleRev;
import ru.citeck.ecos.apps.app.module.EcosModuleService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.*;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDAO;
import ru.citeck.ecos.records2.source.dao.local.RecordsMetaLocalDAO;
import ru.citeck.ecos.records2.source.dao.local.RecordsQueryLocalDAO;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class EcosModuleRecords extends LocalRecordsDAO
                               implements RecordsQueryLocalDAO,
                                          RecordsMetaLocalDAO<EcosModuleRecords.Value> {

    public static final String ID = "module";

    private final EcosModuleService ecosModuleService;
    private final EappsModuleService eappsModuleService;
    private final MetaValuesConverter valuesConverter;

    public EcosModuleRecords(EcosModuleService ecosModuleService,
                             EappsModuleService eappsModuleService,
                             MetaValuesConverter valuesConverter) {
        setId(ID);
        this.valuesConverter = valuesConverter;
        this.ecosModuleService = ecosModuleService;
        this.eappsModuleService = eappsModuleService;
    }

    @Override
    public RecordsQueryResult<RecordRef> getLocalRecords(RecordsQuery query) {

        List<RecordRef> resultRefs = ecosModuleService.getAllModules().stream().map(m -> {
            String typeId = eappsModuleService.getTypeId(m.getClass());
            return RecordRef.valueOf(typeId + "-" + m.getId());
        }).collect(Collectors.toList());

        RecordsQueryResult<RecordRef> result = new RecordsQueryResult<>();
        result.setRecords(resultRefs);

        return result;
    }

    @Override
    public List<Value> getMetaValues(List<RecordRef> records) {

        return records.stream().map(r -> {

            String id = r.getId();

            int delimIdx = id.indexOf("-");
            String type = id.substring(0, delimIdx);
            String moduleId = id.substring(delimIdx + 1);

            EcosModuleRev lastModuleRev = ecosModuleService.getLastModuleRev(type, moduleId);
            EcosModule module = eappsModuleService.read(lastModuleRev.getData(), type);

            return new Value(valuesConverter.toMetaValue(module), type);

        }).collect(Collectors.toList());
    }

    class Value extends MetaValueDelegate {

        String type;

        Value(MetaValue impl, String type) {
            super(impl);
            this.type = type;
        }

        @Override
        public String getId() {
            return type + "$" + super.getId();
        }
    }
}