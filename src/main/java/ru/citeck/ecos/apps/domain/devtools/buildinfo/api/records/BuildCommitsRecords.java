package ru.citeck.ecos.apps.domain.devtools.buildinfo.api.records;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.buildinfo.dto.BuildInfo;
import ru.citeck.ecos.apps.app.domain.buildinfo.dto.CommitInfo;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.element.elematts.RecordAttsElement;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.predicate.model.VoidPredicate;
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts;
import ru.citeck.ecos.records3.record.atts.value.impl.EmptyAttValue;
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao;
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao;
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BuildCommitsRecords extends AbstractRecordsDao implements RecordsQueryDao, RecordAttsDao {

    public static final String ID = "build-commits";

    private final BuildInfoRecords buildInfoRecords;
    private final PredicateService predicateService;

    @Nullable
    @Override
    public Object queryRecords(@NotNull RecordsQuery recsQuery) {

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
        if (recsQuery.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {
            Predicate predicate = recsQuery.getQuery(Predicate.class);
            if (!(predicate instanceof VoidPredicate)) {
                filter = record -> {
                    RecordAtts meta = new RecordAtts();
                    meta.set("build.repo", record.getBuild().getRepo());
                    return predicateService.isMatch(RecordAttsElement.create(meta), predicate);
                };
            }
        }

        int max = recsQuery.getPage().getMaxItems();
        if (max < 0) {
            max = 1000;
        }

        return new RecsQueryRes<>(records.stream()
            .filter(filter)
            .skip(recsQuery.getPage().getSkipCount())
            .limit(max)
            .collect(Collectors.toList()));
    }

    @Nullable
    @Override
    public Object getRecordAtts(@NotNull String recordId) {
        return EmptyAttValue.INSTANCE;
    }

    @NotNull
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
