package ru.citeck.ecos.apps.app.module.records;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.EcosAppsApiFactory;
import ru.citeck.ecos.apps.app.PublishPolicy;
import ru.citeck.ecos.apps.app.PublishStatus;
import ru.citeck.ecos.apps.app.module.*;
import ru.citeck.ecos.apps.utils.EappZipUtils;
import ru.citeck.ecos.apps.utils.io.mem.EappMemDir;
import ru.citeck.ecos.records2.QueryContext;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.*;
import ru.citeck.ecos.records2.objdata.DataValue;
import ru.citeck.ecos.records2.objdata.ObjectData;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.PredicateUtils;
import ru.citeck.ecos.records2.predicate.model.AndPredicate;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.predicate.model.Predicates;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.mutation.RecordsMutation;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.scalar.MLText;
import ru.citeck.ecos.records2.source.dao.MutableRecordsDAO;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDAO;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDAO;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryDAO;
import ru.citeck.ecos.records2.utils.json.JsonUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EcosModuleRecords extends LocalRecordsDAO
                               implements LocalRecordsQueryDAO,
                                          MutableRecordsDAO,
                                          LocalRecordsMetaDAO<MetaValue> {

    public static final String MODULES_SOURCE = "mutation";

    public static final String ID = "module";
    public static final String MODULE_REF_PREFIX = "eapps/" + ID + "@";

    private static final String ATT_MODULE_ID = "module_id";

    private final EcosModuleService ecosModuleService;
    private final MetaValuesConverter valuesConverter;
    private final EappsModuleService eappsModuleService;
    private final EcosAppsApiFactory apiFactory;

    public EcosModuleRecords(EcosModuleService ecosModuleService,
                             EappsModuleService eappsModuleService,
                             MetaValuesConverter valuesConverter,
                             EcosAppsApiFactory apiFactory) {
        setId(ID);
        this.apiFactory = apiFactory;
        this.valuesConverter = valuesConverter;
        this.ecosModuleService = ecosModuleService;
        this.eappsModuleService = eappsModuleService;
    }

    private Predicate convertCriteria(String criteria) {

        AndPredicate pred = new AndPredicate();

        ObjectNode node = JsonUtils.read(criteria, ObjectNode.class);

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
    public RecordsQueryResult<RecordRef> queryLocalRecords(RecordsQuery recordsQuery) {

        String language = recordsQuery.getLanguage();

        Query query;
        switch (language) {

            case PredicateService.LANGUAGE_PREDICATE:

                Predicate predicate = recordsQuery.getQuery(Predicate.class);
                query = PredicateUtils.convertToDto(predicate, Query.class);
                break;

            case "criteria":

                String criteriaText = recordsQuery.getQuery(ObjectNode.class).asText();
                query = PredicateUtils.convertToDto(convertCriteria(criteriaText), Query.class);

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
    public List<MetaValue> getLocalRecordsMeta(List<RecordRef> records, MetaField metaField) {

        return records.stream().map(r -> {

            ModuleRef moduleRef = ModuleRef.valueOf(r.getId());

            if (StringUtils.isBlank(moduleRef.getId())) {
                return new NewValue(moduleRef.getType());
            }

            EcosModuleRev lastModuleRev = ecosModuleService.getLastModuleRev(moduleRef);
            if (lastModuleRev == null) {
                return EmptyValue.INSTANCE;
            }

            ModulePublishState publishState = ecosModuleService.getPublishState(moduleRef);
            EcosModule module = eappsModuleService.read(lastModuleRev.getData(), moduleRef.getType());

            return new ModuleValue(valuesConverter.toMetaValue(module), moduleRef, module, publishState);

        }).collect(Collectors.toList());
    }

    @Override
    public RecordsMutResult mutate(RecordsMutation mutation) {

        List<ModuleRef> resultRefs = mutateImpl(mutation.getRecords());
        Set<ModuleRef> unpublished = new HashSet<>(resultRefs);

        long timeToWait = System.currentTimeMillis() + 10_000;

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

            ModuleRef ref = ModuleRef.valueOf(record.getId().getId());

            Map<String, Object> data = new HashMap<>();

            if (record.has(RecordConstants.ATT_CONTENT)) {

                String base64Content = record.get("/" + RecordConstants.ATT_CONTENT + "/0/url", "");
                base64Content = base64Content.replaceAll("^data:application/json;base64,", "");

                EappMemDir dir = new EappMemDir("upload");
                dir.createFile("module.json", Base64.getDecoder().decode(base64Content));

                byte[] moduleData = EappZipUtils.writeZipAsBytes(dir);

                EcosModule module = eappsModuleService.read(moduleData, ref.getType());

                record.setAttributes(new ObjectData(module));
            }

            AtomicReference<String> moduleId = new AtomicReference<>(ref.getId());

            record.forEach((att, value) -> {
                if (ATT_MODULE_ID.equals(att)) {
                    moduleId.set(value.asText());
                    data.put("id", value);
                } else {
                    data.put(att, convertMutAtt(value));
                }
            });

            String moduleIdAfterUpload;

            if (StringUtils.isBlank(moduleId.get())) {

                Class<EcosModule> typeClass = eappsModuleService.getTypeClass(ref.getType());
                String moduleKey = eappsModuleService.getModuleKey(JsonUtils.convert(data, typeClass));

                EcosModuleRev lastRev = ecosModuleService.getLastModuleRevByKey(ref.getType(), moduleKey);
                if (lastRev != null) {

                    EcosModule module = eappsModuleService.read(lastRev.getData(), ref.getType());
                    JsonUtils.applyData(data, module);

                    moduleIdAfterUpload = ecosModuleService.uploadModule(MODULES_SOURCE, module, PublishPolicy.PUBLISH);

                } else {

                    data.put("id", UUID.randomUUID().toString());
                    EcosModule module = JsonUtils.convert(data, typeClass);

                    moduleIdAfterUpload = ecosModuleService.uploadModule(MODULES_SOURCE, module, PublishPolicy.PUBLISH);
                }

            } else {

                EcosModuleRev lastModuleRev = ecosModuleService.getLastModuleRev(ref);
                if (lastModuleRev == null) {
                    throw new IllegalArgumentException("Module is not found with ref '" + ref + "'");
                }

                EcosModule module = eappsModuleService.read(lastModuleRev.getData(), ref.getType());
                JsonUtils.applyData(data, module);

                moduleIdAfterUpload = ecosModuleService.uploadModule(MODULES_SOURCE, module, PublishPolicy.PUBLISH);
            }

            resultRefs.add(ModuleRef.create(ref.getType(), moduleIdAfterUpload));
        }

        return resultRefs;
    }

    private Object convertMutAtt(DataValue value) {
        if (value == null) {
            return null;
        }
        if (value.isTextual()) {
            String strValue = value.asText();
            if (strValue.startsWith(MODULE_REF_PREFIX)) {
                return strValue.replace(MODULE_REF_PREFIX, "");
            }
        } else if (value.isArray()) {
            List<Object> result = new ArrayList<>();
            for (DataValue node : value) {
                result.add(convertMutAtt(node));
            }
            return result;
        } else if (value.isObject()) {
            Map<String, Object> result = new HashMap<>();
            value.forEach((fieldName, innerValue) -> result.put(fieldName, convertMutAtt(innerValue)));
            return result;
        }
        return value;
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion deletion) {

        List<RecordMeta> resultRefs = new ArrayList<>();

        List<ModuleRef> moduleRefs = new ArrayList<>();

        deletion.getRecords().forEach(r -> {
            ModuleRef ref = ModuleRef.valueOf(r.getId());
            moduleRefs.add(ref);
            apiFactory.getModuleApi().deleteModule(UUID.randomUUID().toString(), ref);
            resultRefs.add(new RecordMeta(r));
        });

        long timeToWait = System.currentTimeMillis() + 5_000;

        int deletedIdx = 0;
        boolean isDeleted;
        do {
            ModuleRef ref = moduleRefs.get(deletedIdx);
            isDeleted = !ecosModuleService.isExists(ref);
            if (isDeleted) {
                deletedIdx++;
            } else {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        } while (deletedIdx < moduleRefs.size() && System.currentTimeMillis() < timeToWait);

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

    private Object convertAttValue(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof ModuleRef) {
            return RecordRef.create("eapps", EcosModuleRecords.ID, value.toString());
        } else if (value instanceof Collection) {
            List<Object> converted = new ArrayList<>();
            for (Object elem : (Collection) value) {
                converted.add(convertAttValue(elem));
            }
            return converted;
        }
        return new InnerValue(value);
    }

    class InnerValue extends MetaValueDelegate {

        public InnerValue(Object value) {
            super(valuesConverter.toMetaValue(value));
        }

        @Override
        public Object getAttribute(String name, MetaField field) throws Exception {
            switch (name) {
                case "_state":
                case "_alias":
                case "submit":
                case "permissions":
                    return null;
            }
            return convertAttValue(super.getAttribute(name, field));
        }
    }

    class ModuleValue extends MetaValueDelegate {

        EcosModule module;
        ModuleRef ref;
        ModulePublishState publishState;

        ModuleValue(MetaValue impl, ModuleRef ref, EcosModule module, ModulePublishState publishState) {
            super(impl);
            this.ref = ref;
            this.module = module;
            this.publishState = publishState;
        }

        @Override
        public Object getAttribute(String name, MetaField field) throws Exception {

            switch (name) {
                case ATT_MODULE_ID:
                    return super.getId();
                case RecordConstants.ATT_FORM_KEY:
                    return eappsModuleService.getFormKey(module);
                case RecordConstants.ATT_TYPE:
                    return ref.getType();
                case RecordConstants.ATT_FORM_MODE:
                    return RecordConstants.FORM_MODE_EDIT;
                case "publishMsg":
                    return publishState.getMsg();
                case "publishStatus":
                    return publishState.getStatus().toString();
                case "_state":
                case "_alias":
                case "submit":
                case "permissions":
                case "cm:modified":
                case "pendingUpdate":
                    return null;
            }

            return convertAttValue(super.getAttribute(name, field));
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
                    if (name instanceof MLText) {
                        return ((MLText) name).getClosestValue(QueryContext.getCurrent().getLocale());
                    } else {
                        return name.toString();
                    }
                }
            } catch (Exception e) {
                return ref.toString();
            }
            return ref.toString();
        }

        @Override
        public String getId() {
            return ref.toString();
        }

        @Override
        public String getString() {
            return ref.toString();
        }
    }

    @Data
    public static class Query {

        private String type;
        private Predicate predicate;
    }
}
