package ru.citeck.ecos.apps.domain.buildinfo.api.records;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.buildinfo.dto.BuildInfo;
import ru.citeck.ecos.apps.app.remote.AppStatus;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.EmptyValue;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class BuildInfoRecords extends LocalRecordsDao
    implements LocalRecordsQueryWithMetaDao<Object>, LocalRecordsMetaDao<Object> {

    public static final String ID = "build-info";

    private final Map<String, Record> fullBuildInfo = new ConcurrentHashMap<>();

    @Override
    public RecordsQueryResult<Object> queryLocalRecords(@NotNull RecordsQuery recordsQuery,
                                                        @NotNull MetaField metaField) {

        return new RecordsQueryResult<>(new ArrayList<>(fullBuildInfo.values()));
    }

    @Override
    public List<Object> getLocalRecordsMeta(@NotNull List<RecordRef> records,
                                            @NotNull MetaField metaField) {

        return records.stream().map( ref -> {
            Record rec = fullBuildInfo.get(ref.getId());
            if (rec != null) {
                return rec;
            } else {
                return EmptyValue.INSTANCE;
            }
        }).collect(Collectors.toList());
    }

    public void register(AppStatus app, List<BuildInfo> buildInfo) {
        for (BuildInfo info : buildInfo) {
            String id = app.getAppName() + "-" + info.getRepo();
            Record currentInfo = fullBuildInfo.get(id);
            if (currentInfo == null || currentInfo.info.getBuildDate().isBefore(info.getBuildDate())) {
                fullBuildInfo.put(id, new Record(id, app, info));
            }
        }
    }

    @Override
    public String getId() {
        return ID;
    }

    @Data
    @RequiredArgsConstructor
    public static class Record {
        private final String id;
        private final AppStatus app;
        private final BuildInfo info;
    }
}
