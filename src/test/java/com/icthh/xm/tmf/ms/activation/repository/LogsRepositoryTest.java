package com.icthh.xm.tmf.ms.activation.repository;

import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_START;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;

import com.github.database.rider.core.api.dataset.DataSet;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaLogType;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@DataSet(value = "init-logs.xml")
public class LogsRepositoryTest extends BaseDaoTest {

    @Autowired
    private SagaLogRepository sagaLogRepository;

    @Autowired
    private SagaTransactionRepository sagaTransactionRepository;

    @Test
    public void finishedLogsTest() {
        List<SagaLog> finishLogs = sagaLogRepository.getFinishLogs("1", asList("STARTED", "STARTED2", "FINISHED", "FINISHED2"));
        assertEquals(asList(sagaLog("FINISHED", 3L, "1"), sagaLog("FINISHED2", 5L, "1")), finishLogs);
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
        assertEquals(asList(sagaLog("FINISHED", 3L, "1")), finished);
    }

    @Test
    public void findLogs() {
        List<SagaLog> started = sagaLogRepository.findLogs(EVENT_START, sagaTransactionRepository.getOne("1"), "STARTED");
        assertEquals(1, started.size());
        assertEquals(asList(sagaLog("STARTED", 1L, "1").setLogType(EVENT_START)), started);
    }



    private SagaLog sagaLog(String finished, long id, String key) {
        return new SagaLog().setEventTypeKey(finished).setLogType(SagaLogType.EVENT_END)
            .setSagaTransaction(new SagaTransaction().setId("1").setTypeKey("A").setKey(key)).setId(id);
    }

}
