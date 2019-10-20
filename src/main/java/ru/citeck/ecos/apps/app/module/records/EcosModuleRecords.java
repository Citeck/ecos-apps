package ru.citeck.ecos.apps.app.module.records;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.app.PublishStatus;
import ru.citeck.ecos.apps.app.module.*;
import ru.citeck.ecos.apps.config.Constants;
import ru.citeck.ecos.apps.security.SecurityUtils;
import ru.citeck.ecos.predicate.PredicateService;
import ru.citeck.ecos.predicate.PredicateUtils;
import ru.citeck.ecos.predicate.model.AndPredicate;
import ru.citeck.ecos.predicate.model.Predicate;
import ru.citeck.ecos.predicate.model.Predicates;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.*;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.mutation.RecordsMutation;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.MutableRecordsDAO;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDAO;
import ru.citeck.ecos.records2.source.dao.local.RecordsMetaLocalDAO;
import ru.citeck.ecos.records2.source.dao.local.RecordsQueryLocalDAO;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EcosModuleRecords extends LocalRecordsDAO
                               implements RecordsQueryLocalDAO,
                                          MutableRecordsDAO,
                                          RecordsMetaLocalDAO<MetaValue> {

    public static final String MODULES_SOURCE = "mutation";

    public static final String ID = "module";
    public static final String MODULE_REF_PREFIX = "eapps/" + ID + "@";

    private static final String ATT_MODULE_ID = "module_id";

    private final PredicateService predicateService;
    private final EcosModuleService ecosModuleService;
    private final MetaValuesConverter valuesConverter;
    private final EappsModuleService eappsModuleService;

    public EcosModuleRecords(EcosModuleService ecosModuleService,
                             EappsModuleService eappsModuleService,
                             MetaValuesConverter valuesConverter,
                             PredicateService predicateService) {
        setId(ID);
        this.valuesConverter = valuesConverter;
        this.predicateService = predicateService;
        this.ecosModuleService = ecosModuleService;
        this.eappsModuleService = eappsModuleService;
    }

    private Predicate convertCriteria(String criteria) {

        AndPredicate pred = new AndPredicate();

        ObjectNode node;
        try {
            node = (ObjectNode) objectMapper.readTree(criteria);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int i = 0;
        String field = "field_" + i;
        while (node.has(field)) {

            String att = node.get(field).asText();
            String val = node.get("value_" + i).asText();

            pred.addPredicate(Predicates.eq(att, val));

            field = "field_" + (++i);
        }

        return pred;
    }

    @Override
    public RecordsQueryResult<RecordRef> getLocalRecords(RecordsQuery recordsQuery) {

        String language = recordsQuery.getLanguage();

        Query query;
        switch (language) {

            case PredicateService.LANGUAGE_PREDICATE:

                Predicate predicate = predicateService.readJson(recordsQuery.getQuery());
                query = PredicateUtils.convertToDto(predicate, Query.class);
                break;

            case "criteria":

                query = PredicateUtils.convertToDto(convertCriteria(recordsQuery.getQuery().asText()), Query.class);

                break;
            default:
                query = recordsQuery.getQuery(Query.class);
        }

        int skipCount = recordsQuery.getSkipCount();
        int maxItems = recordsQuery.getMaxItems();

        if (maxItems < 0) {
            maxItems = 1000;
        }

        List<EcosModule> modules;
        int totalCount;

        if (query.type != null) {
            modules = ecosModuleService.getModules(query.type, skipCount, maxItems);
            totalCount = ecosModuleService.getCount(query.type);
        } else {
            modules = ecosModuleService.getAllModules(skipCount, maxItems);
            totalCount = ecosModuleService.getCount();
        }

        List<RecordRef> resultRefs = modules.stream().map(m -> {
            String typeId = eappsModuleService.getTypeId(m.getClass());
            return RecordRef.valueOf(typeId + "$" + m.getId());
        }).collect(Collectors.toList());

        RecordsQueryResult<RecordRef> result = new RecordsQueryResult<>();
        result.setRecords(resultRefs);
        result.setHasMore(skipCount + modules.size() < totalCount);
        result.setTotalCount(totalCount);

        return result;
    }

    @Override
    public List<MetaValue> getMetaValues(List<RecordRef> records) {

        return records.stream().map(r -> {

            ModuleRef moduleRef = ModuleRef.valueOf(r.getId());

            if (StringUtils.isBlank(moduleRef.getId())) {
                return new NewValue(moduleRef.getType());
            }

            EcosModuleRev lastModuleRev = ecosModuleService.getLastModuleRev(moduleRef);
            if (lastModuleRev == null) {
                throw new IllegalArgumentException("Module is not found for ref " + moduleRef);
            }

            EcosModule module = eappsModuleService.read(lastModuleRev.getData(), moduleRef.getType());

            return new Value(valuesConverter.toMetaValue(module), moduleRef.getType());

        }).collect(Collectors.toList());
    }

    @Override
    public RecordsMutResult mutate(RecordsMutation mutation) {

        List<ModuleRef> resultRefs = mutateImpl(mutation.getRecords());
        Set<ModuleRef> unpublished = new HashSet<>(resultRefs);

        long timeToWait = System.currentTimeMillis() + 5_000;

        while (!unpublished.isEmpty() && System.currentTimeMillis() < timeToWait) {

            ModuleRef moduleRef = unpublished.stream().findFirst().orElse(null);

            PublishStatus status = ecosModuleService.getPublishStatus(moduleRef);
            if (PublishStatus.PUBLISHED.equals(status)
                    || PublishStatus.PUBLISH_FAILED.equals(status)) {
                unpublished.remove(moduleRef);
            } else {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        if (!unpublished.isEmpty()) {
            log.warn("Modules publishing time out. Unpublished modules: " + unpublished);
        }

        List<RecordMeta> resultList = resultRefs.stream()
            .map(r -> new RecordMeta(RecordRef.valueOf(r.toString())))
            .collect(Collectors.toList());

        RecordsMutResult mutResult = new RecordsMutResult();
        mutResult.setRecords(resultList);
        return mutResult;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ModuleRef> mutateImpl(List<RecordMeta> records) {

        List<ModuleRef> resultRefs = new ArrayList<>();

        for (RecordMeta record : records) {

            Map<String, Object> data = new HashMap<>();

            record.forEach((att, value) -> {
                if (ATT_MODULE_ID.equals(att)) {
                    data.put("id", value);
                } else {
                    data.put(att, convertMutAtt(value));
                }
            });

            ModuleRef ref = ModuleRef.valueOf(record.getId().getId());

            String moduleIdAfterUpload;

            if (StringUtils.isBlank(ref.getId())) {

                Class<EcosModule> typeClass = eappsModuleService.getTypeClass(ref.getType());
                EcosModule module = objectMapper.convertValue(data, typeClass);

                moduleIdAfterUpload = ecosModuleService.uploadModule(MODULES_SOURCE, module);

            } else {

                EcosModuleRev lastModuleRev = ecosModuleService.getLastModuleRev(ref);
                if (lastModuleRev == null) {
                    throw new IllegalArgumentException("Module is not found with ref '" + ref + "'");
                }

                EcosModule module = eappsModuleService.read(lastModuleRev.getData(), ref.getType());
                ObjectReader reader = objectMapper.readerForUpdating(module);

                try {
                    reader.readValue(objectMapper.convertValue(data, ObjectNode.class));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                moduleIdAfterUpload = ecosModuleService.uploadModule(MODULES_SOURCE, module);
            }

            resultRefs.add(ModuleRef.create(ref.getType(), moduleIdAfterUpload));
        }

        return resultRefs;
    }

    private Object convertMutAtt(JsonNode value) {
        if (value.isTextual()) {
            String strValue = value.asText();
            if (strValue.startsWith(MODULE_REF_PREFIX)) {
                return strValue.replace(MODULE_REF_PREFIX, "");
            }
        }
        return value;
    }

    @Override
    @Transactional
    public RecordsDelResult delete(RecordsDeletion deletion) {

        List<RecordMeta> resultRefs = new ArrayList<>();

        deletion.getRecords().forEach(r -> {
            ecosModuleService.delete(ModuleRef.valueOf(r.getId()));
            resultRefs.add(new RecordMeta(r));
        });

        RecordsDelResult result = new RecordsDelResult();
        result.setRecords(resultRefs);

        return result;
    }

    class NewValue implements MetaValue {

        final String type;

        NewValue(String type) {
            this.type = type;
        }

        @Override
        public Object getAttribute(String name, MetaField field) {
            switch (name) {
                case RecordConstants.ATT_FORM_KEY:
                    return "module_" + type;
                case RecordConstants.ATT_TYPE:
                    return type;
                case RecordConstants.ATT_FORM_MODE:
                    return RecordConstants.FORM_MODE_CREATE;
            }
            return null;
        }

        @Override
        public String getDisplayName() {
            return StringUtils.capitalize(type);
        }
    }

    class Value extends MetaValueDelegate {

        String type;

        Value(MetaValue impl, String type) {
            super(impl);
            this.type = type;
        }

        @Override
        public Object getAttribute(String name, MetaField field) throws Exception {

            switch (name) {
                case ATT_MODULE_ID:
                    return super.getId();
                case RecordConstants.ATT_FORM_KEY:
                    return "module_" + type;
                case RecordConstants.ATT_TYPE:
                    return type;
                case RecordConstants.ATT_FORM_MODE:
                    return RecordConstants.FORM_MODE_EDIT;
                case "_state":
                case "_alias":
                case "submit":
                    return null;
            }

            Object value = super.getAttribute(name, field);

            if (value instanceof ModuleRef) {
                return RecordRef.create("eapps", EcosModuleRecords.ID, value.toString());
            }
            return value;
        }

        @Override
        public MetaEdge getEdge(String name, MetaField field) {
            return new SimpleMetaEdge(name, this);
        }

        @Override
        public String getDisplayName() {

            //todo: with annotations
            try {
                Object name = getImpl().getAttribute("name", null);
                if (name == null) {
                    name = getImpl().getAttribute("title", null);
                }
                if (name != null) {
                    return name.toString();
                }
            } catch (Exception e) {
                return super.getDisplayName();
            }
            return super.getDisplayName();
        }

        @Override
        public String getId() {
            return type + "$" + super.getId();
        }
    }

    @Data
    public static class Query {

        private String type;
        private Predicate predicate;
    }
}
