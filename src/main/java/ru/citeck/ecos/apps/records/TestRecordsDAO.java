package ru.citeck.ecos.apps.records;

import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDAO;
import ru.citeck.ecos.records2.source.dao.local.RecordsQueryWithMetaLocalDAO;

@Component
public class TestRecordsDAO extends LocalRecordsDAO implements RecordsQueryWithMetaLocalDAO<TestRecordsDAO.Test> {

    public TestRecordsDAO() {
        setId("");
    }

    @Override
    public RecordsQueryResult<Test> getMetaValues(RecordsQuery recordsQuery) {

        RecordsQueryResult<Test> result = new RecordsQueryResult<>();

        result.addRecord(new Test("231"));
        result.addRecord(new Test("23132"));
        result.addRecord(new Test("3213123"));
        result.addRecord(new Test("3213123123"));

        return result;
    }

    public class Test {

        private String id = "23213";

        private String testField0 = "field0";
        private String testField1 = "field12213";

        public Test(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTestField0() {
            return testField0;
        }

        public void setTestField0(String testField0) {
            this.testField0 = testField0;
        }

        public String getTestField1() {
            return testField1;
        }

        public void setTestField1(String testField1) {
            this.testField1 = testField1;
        }
    }
}
