package com.icthh.xm.tmf.ms.activation.repository;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;

import com.github.database.rider.core.api.dataset.DataSet;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaLogType;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Slf4j
@DataSet(value = "init-logs.xml")
public class LogsRepositoryTest extends BaseDaoTest {

    @Autowired
    private SagaLogRepository sagaLogRepository;

    @Test
    public void finishedLogsTest() {
        List<SagaLog> finishLogs = sagaLogRepository.getFinishLogs("1", asList("STARTED", "STARTED2", "FINISHED", "FINISHED2"));
        assertEquals(asList(sagaLog("FINISHED", 3L), sagaLog("FINISHED2", 5L)), finishLogs);
    }

    @Test
    public void emptyListIfTaskNotFinished() {
        List<SagaLog> finished = sagaLogRepository.getFinishLogs("1", asList("STARTED"));
        assertEquals(0, finished.size());
        assertEquals(emptyList(), finished);
    }

    @Test
    public void oneLogIfNotFinished() {
        List<SagaLog> finished = sagaLogRepository.getFinishLogs("1", asList("FINISHED"));
        assertEquals(1, finished.size());
        assertEquals(asList(sagaLog("FINISHED", 3L)), finished);
    }

    private SagaLog sagaLog(String finished, long id) {
        return new SagaLog().setEventTypeKey(finished).setLogType(SagaLogType.EVENT_END)
            .setSagaTransaction(new SagaTransaction().setId("1").setTypeKey("A")).setId(id);
    }

}
