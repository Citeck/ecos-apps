package ru.citeck.ecos.apps.domain.artifact.patch.api.records;

//@Slf4j
//@Component
public class ArtifactPatchRecords/* extends LocalRecordsDao
                                implements LocalRecordsQueryWithMetaDao<ArtifactPatchRecords.PatchRecord>,
                                           LocalRecordsMetaDao<ArtifactPatchRecords.PatchRecord>,
                                           MutableRecordsLocalDao<ArtifactPatchRecords.PatchRecord> */{

    /*public static final String ID = "module-patch";

    private final ArtifactPatchService artifactPatchService;

    @Autowired
    public ArtifactPatchRecords(ArtifactPatchService artifactPatchService,
                                RecordsService recordsService) {
        setId(ID);
        this.artifactPatchService = artifactPatchService;
        this.recordsService = recordsService;
    }

    @Override
    public RecordsQueryResult<PatchRecord> queryLocalRecords(RecordsQuery recordsQuery, MetaField metaField) {

        Predicate query = recordsQuery.getQuery(Predicate.class);

        RecordsQueryResult<PatchRecord> result = new RecordsQueryResult<>();

        result.setRecords(artifactPatchService.getAll(recordsQuery.getMaxItems(), recordsQuery.getSkipCount(), query)
            .stream()
            .map(PatchRecord::new)
            .collect(Collectors.toList()));
        result.setTotalCount(artifactPatchService.getCount(query));

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

        return artifactPatchService.getPatchById(ref.getId())
            .map(PatchRecord::new)
            .orElseThrow(() -> new IllegalArgumentException("Patch with id '" + ref + "' is not found!"));
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion recordsDeletion) {
        List<RecordMeta> resultMeta = new ArrayList<>();
        recordsDeletion.getRecords().forEach(r -> {
            artifactPatchService.delete(r.getId());
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
        *//*RecordsMutResult result = new RecordsMutResult();
        values.forEach(value -> {
            ArtifactPatchDto dashboardDto = artifactPatchService.save(value);
            result.addRecord(new RecordMeta(RecordRef.valueOf(dashboardDto.getId())));
        });*//*
        //return result;
        return null;
    }

    public static class PatchRecord extends ArtifactPatchDto {

        PatchRecord() {
        }

        PatchRecord(ArtifactPatchDto dto) {
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
        public ArtifactPatchDto toJson() {
            return new ArtifactPatchDto(this);
        }
    }*/
}