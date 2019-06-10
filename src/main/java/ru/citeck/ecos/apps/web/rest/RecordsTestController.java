package ru.citeck.ecos.apps.web.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
import ru.citeck.ecos.records2.request.query.QueryConsistency;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.request.result.RecordsResult;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/records")
public class RecordsTestController {

    private RecordsService recordsService;

    @Autowired
    public RecordsTestController(RecordsService recordsService) {
        this.recordsService = recordsService;
    }

    @GetMapping("/test/authorities")
    public Authorities testAuthorities() {
        return recordsService.getMeta(RecordRef.valueOf("alfresco@people@admin"), Authorities.class);
    }

    @GetMapping("/test/sites")
    public RecordsQueryResult<SiteInfo> testSiteInfo() {

        RecordsQuery recordsQuery = new RecordsQuery();
        recordsQuery.setQuery("TYPE:\"st:site\"");
        recordsQuery.setSourceId("alfresco");
        recordsQuery.setLanguage("fts-alfresco");
        recordsQuery.setConsistency(QueryConsistency.EVENTUAL);

        return recordsService.queryRecords(recordsQuery, SiteInfo.class);
    }

    @GetMapping("/test/edges")
    public RecordsResult<RecordMeta> testEdge() {

        String schema = "a:edge(n:\"cm:title\"){type,editorKey,javaClass}," +
                       "b:edge(n:\"idocs:currencyName\"){type,editorKey,javaClass}," +
                       "c:edge(n:\"idocs:currencyRate\"){type,editorKey,javaClass}," +
                       "d:edge(n:\"idocs:currencyCode\"){type,editorKey,javaClass}";

        return recordsService.getMeta(Collections.singletonList(RecordRef.valueOf("alfresco@")), schema);
    }

    public static class SiteInfo {

        private String id;

        @MetaAtt("cm:title")
        private String title;

        @MetaAtt("cm:name")
        private String name;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    public static class Authorities {

        @MetaAtt(".att(n:'authorities'){atts(n:'list')}")
        private List<String> authorities;

        public List<String> getAuthorities() {
            return authorities;
        }

        public void setAuthorities(List<String> authorities) {
            this.authorities = authorities;
        }
    }
}
