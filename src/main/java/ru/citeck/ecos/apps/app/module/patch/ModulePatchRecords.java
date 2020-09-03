package ru.citeck.ecos.apps.app.module.patch;

import ecos.com.fasterxml.jackson210.annotation.JsonIgnore;
import ecos.com.fasterxml.jackson210.annotation.JsonProperty;
import ecos.com.fasterxml.jackson210.annotation.JsonValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ModulePatchRecords extends LocalRecordsDao
                                implements LocalRecordsQueryWithMetaDao<ModulePatchRecords.PatchRecord>,
                                           LocalRecordsMetaDao<ModulePatchRecords.PatchRecord>,
                                           MutableRecordsLocalDao<ModulePatchRecords.PatchRecord> {

    public static final String ID = "module-patch";

    private final ModulePatchService modulePatchService;

    @Autowired
    public ModulePatchRecords(ModulePatchService modulePatchService,
                              RecordsService recordsService) {
        setId(ID);
        this.modulePatchService = modulePatchService;
        this.recordsService = recordsService;
    }

    @Override
    public RecordsQueryResult<PatchRecord> queryLocalRecords(RecordsQuery recordsQuery, MetaField metaField) {

        Predicate query = recordsQuery.getQuery(Predicate.class);

        RecordsQueryResult<PatchRecord> result = new RecordsQueryResult<>();

        result.setRecords(modulePatchService.getAll(recordsQuery.getMaxItems(), recordsQuery.getSkipCount(), query)
            .stream()
            .map(PatchRecord::new)
            .collect(Collectors.toList()));
        result.setTotalCount(modulePatchService.getCount(query));

        return result;
    }

    @Override
    public List<PatchRecord> getLocalRecordsMeta(List<RecordRef> list, MetaField metaField) {
        return list.stream()
            .map(this::getModulePatchByRef)
            .collect(Collectors.toList());
    }

    private PatchRecord getModulePatchByRef(RecordRef ref) {

        if (ref.getId().isEmpty()) {
            return new PatchRecord();
        }

        return modulePatchService.getPatchById(ref.getId())
            .map(PatchRecord::new)
            .orElseThrow(() -> new IllegalArgumentException("Patch with id '" + ref + "' is not found!"));
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion recordsDeletion) {
        List<RecordMeta> resultMeta = new ArrayList<>();
        recordsDeletion.getRecords().forEach(r -> {
            modulePatchService.delete(r.getId());
            resultMeta.add(new RecordMeta(r));
        });
        RecordsDelResult result = new RecordsDelResult();
        result.setRecords(resultMeta);
        return result;
    }

    @Override
    public List<PatchRecord> getValuesToMutate(List<RecordRef> list) {
        return list.stream()
            .map(ref -> new PatchRecord(getModulePatchByRef(ref)))
            .collect(Collectors.toList());
    }

    @Override
    public RecordsMutResult save(List<PatchRecord> values) {
        RecordsMutResult result = new RecordsMutResult();
        values.forEach(value -> {
            ModulePatchDto dashboardDto = modulePatchService.save(value);
            result.addRecord(new RecordMeta(RecordRef.valueOf(dashboardDto.getId())));
        });
        return result;
    }

    public static class PatchRecord extends ModulePatchDto {

        PatchRecord() {
        }

        PatchRecord(ModulePatchDto dto) {
            super(dto);
        }

        public String getModuleId() {
            return getId();
        }

        public void setModuleId(String value) {
            setId(value);
        }

        @JsonIgnore
        @MetaAtt(".disp")
        public String getDisplayName() {
            String result = getId();
            return result != null ? result : "Dashboard";
        }

        @JsonProperty("_content")
        public void setContent(List<ObjectData> content) {

            String base64Content = content.get(0).get("url", "");
            base64Content = base64Content.replaceAll("^data:application/json;base64,", "");
            ObjectData data = Json.getMapper().read(Base64.getDecoder().decode(base64Content), ObjectData.class);

            Json.getMapper().applyData(this, data);
        }

        @JsonValue
        @com.fasterxml.jackson.annotation.JsonValue
        public ModulePatchDto toJson() {
            return new ModulePatchDto(this);
        }
    }
}
