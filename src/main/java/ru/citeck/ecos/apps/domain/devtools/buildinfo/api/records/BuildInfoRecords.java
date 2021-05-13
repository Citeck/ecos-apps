package ru.citeck.ecos.apps.domain.devtools.buildinfo.api.records;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.EmptyValue;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;
import ru.citeck.ecos.apps.domain.devtools.buildinfo.dto.AppBuildInfo;
import ru.citeck.ecos.apps.domain.devtools.buildinfo.dto.BuildInfo;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
//@Component
public class BuildInfoRecords extends LocalRecordsDao implements LocalRecordsQueryWithMetaDao<Object>,
                                                                 LocalRecordsMetaDao<Object> {

    public static final String ID = "build-info";

    private final Map<String, BuildInfoRecord> buildInfo = new ConcurrentHashMap<>();

    public BuildInfoRecords() {

        ObjectData buildInfo = null;

        try {
            File buildInfoFile = ResourceUtils.getFile("classpath:build-info/full.json");
            if (buildInfoFile.exists()) {
                buildInfo = Json.getMapper().read(buildInfoFile, ObjectData.class);
            }
        } catch (FileNotFoundException e) {
            // do nothing
        } catch (Exception e) {
            log.error("Build info reading failed", e);
        }

        if (buildInfo == null) {
            buildInfo = ObjectData.create();
            buildInfo.set("buildDate", new Date().toInstant().toString());
        }
        registerBuildInfo(new AppBuildInfo("uiserv", "UI Server", "", buildInfo));
    }

    @Override
    public RecordsQueryResult<Object> queryLocalRecords(@NotNull RecordsQuery recordsQuery,
                                                        @NotNull MetaField field) {

        return new RecordsQueryResult<>(buildInfo.values()
            .stream()
            .map(BuildInfoRecord::getBuildInfo)
            .collect(Collectors.toList()));
    }

    @Override
    public List<Object> getLocalRecordsMeta(@NotNull List<RecordRef> records, @NotNull MetaField metaField) {

        return records.stream().map(ref -> {
            BuildInfoRecord res = this.buildInfo.get(ref.getId());
            return res != null ? res.buildInfo : EmptyValue.INSTANCE;
        }).collect(Collectors.toList());
    }

    @Override
    public String getId() {
        return ID;
    }

    public void registerBuildInfo(AppBuildInfo appInfo) {

        String buildDateText = appInfo.getInfo().get("buildDate").asText();
        if (StringUtils.isBlank(buildDateText)) {
            return;
        }
        Instant buildDate = Instant.parse(buildDateText);

        BuildInfoRecord currentRecord = this.buildInfo.get(appInfo.getId());
        if (currentRecord != null && !buildDate.isAfter(currentRecord.buildDate)) {
            return;
        }

        ObjectData infoData = appInfo.getInfo().deepCopy();
        if (!infoData.has("commits")) {
            infoData.set("commits", "[]");
        }

        this.buildInfo.put(appInfo.getId(), new BuildInfoRecord(buildDate, BuildInfo.create()
            .withId(appInfo.getId())
            .withDescription(appInfo.getDescription())
            .withLabel(appInfo.getLabel())
            .withInfo(infoData)
            .build()
        ));
    }

    @Data
    @AllArgsConstructor
    private static class BuildInfoRecord {
        final Instant buildDate;
        final BuildInfo buildInfo;
    }
}

