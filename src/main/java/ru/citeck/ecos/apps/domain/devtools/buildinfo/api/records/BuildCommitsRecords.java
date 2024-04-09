package ru.citeck.ecos.apps.domain.devtools.buildinfo.api.records;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.buildinfo.dto.BuildInfo;
import ru.citeck.ecos.apps.app.domain.buildinfo.dto.CommitInfo;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.RecordElement;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.predicate.model.VoidPredicate;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;
import ru.citeck.ecos.records3.record.atts.value.impl.EmptyAttValue;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BuildCommitsRecords extends LocalRecordsDao
    implements LocalRecordsQueryWithMetaDao<Object>, LocalRecordsMetaDao<Object> {

    public static final String ID = "build-commits";

    private final BuildInfoRecords buildInfoRecords;
    private final PredicateService predicateService;

    @Override
    public RecordsQueryResult<Object> queryLocalRecords(@NotNull RecordsQuery recordsQuery,
                                                        @NotNull MetaField metaField) {

        List<CommitRecord> records = new ArrayList<>();
        buildInfoRecords.getAll().forEach(info ->
            info.getInfo().getCommits().forEach(commit ->
                records.add(new CommitRecord(commit, info.getInfo()))
            )
        );
        records.sort((c0, c1) ->
            c1.commit.getCommitter().getDate().compareTo(c0.getCommit().getCommitter().getDate())
        );

        java.util.function.Predicate<CommitRecord> filter = c -> true;
        if (recordsQuery.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {
            Predicate predicate = recordsQuery.getQuery(Predicate.class);
            if (!(predicate instanceof VoidPredicate)) {
                filter = record -> {
                    RecordMeta meta = new RecordMeta();
                    meta.set("build.repo", record.getBuild().getRepo());
                    return predicateService.isMatch(new RecordElement(meta), predicate);
                };
            }
        }

        int max = recordsQuery.getMaxItems();
        if (max < 0) {
            max = 1000;
        }

        return new RecordsQueryResult<>(records.stream()
            .filter(filter)
            .skip(recordsQuery.getSkipCount())
            .limit(max)
            .collect(Collectors.toList()));
    }

    @Override
    public List<Object> getLocalRecordsMeta(@NotNull List<EntityRef> records,
                                            @NotNull MetaField metaField) {

        return records.stream().map(it -> EmptyAttValue.INSTANCE).collect(Collectors.toList());
    }

    @Override
    public String getId() {
        return ID;
    }

    @Data
    @RequiredArgsConstructor
    public static class CommitRecord {
        private final CommitInfo commit;
        private final BuildInfo build;
    }
}
