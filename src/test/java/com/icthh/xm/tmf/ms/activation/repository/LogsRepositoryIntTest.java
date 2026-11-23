package com.icthh.xm.tmf.ms.activation.repository;

import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_START;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.REJECTED_BY_CONDITION;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.github.database.rider.core.api.dataset.DataSet;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaLogType;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@DataSet(value = "init-logs.xml")
public class LogsRepositoryIntTest extends BaseDaoTest {

    @Autowired
    private SagaLogRepository sagaLogRepository;

    @Autowired
    private SagaTransactionRepository sagaTransactionRepository;

    @Test
    public void finishedLogsTest() {
        List<SagaLog> finishLogs = sagaLogRepository.getFinishLogs("1", asList("STARTED", "STARTED2", "FINISHED", "FINISHED2", "REJECTED"));
        assertEquals(asList(sagaLog("FINISHED", 3L, "1"),
            sagaLog("FINISHED2", 5L, "1"), sagaLog("REJECTED", 6L, "1", REJECTED_BY_CONDITION)), finishLogs);
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
    public void finishedLogsTaskTypeKeysTest() {
        List<String> finishLogs = sagaLogRepository.getFinishLogsTypeKeys("1", asList("STARTED", "STARTED2", "FINISHED", "FINISHED2", "REJECTED"));
        assertEquals(asList("FINISHED", "FINISHED2", "REJECTED"), finishLogs);
    }

    @Test
    public void emptyListOfTaskTypeKeysIfTaskNotFinished() {
        List<String> finished = sagaLogRepository.getFinishLogsTypeKeys("1", asList("STARTED"));
        assertEquals(0, finished.size());
        assertEquals(emptyList(), finished);
    }

    @Test
    public void oneTaskTypeKeyLogIfNotFinished() {
        List<String> finished = sagaLogRepository.getFinishLogsTypeKeys("1", asList("FINISHED"));
        assertEquals(1, finished.size());
        assertEquals(List.of("FINISHED"), finished);
    }

    @Test
    public void findLogs() {
        List<SagaLog> started = sagaLogRepository.findLogs(EVENT_START, sagaTransactionRepository.getOne("1"), "STARTED", null);
        assertEquals(1, started.size());
        assertEquals(asList(sagaLog("STARTED", 1L, "1").setLogType(EVENT_START)), started);
    }


    @ParameterizedTest
    @CsvSource({"1, FINISHED, 3", "1, REJECTED, 6", "2, FINISHED, 10"})
    public void findFinishedOrRejectedNonIterableTask(String transactionId, String taskKey, Long logId) {
        assertEquals(logId, sagaLogRepository
            .findFinishLogTypeKeyAndIteration(transactionId, taskKey, null).orElseThrow().getId());
    }

    @ParameterizedTest
    @CsvSource({
        "1, STARTED,",
        "2, REJECTED,",
        "missing, FINISHED,",
        "1, FINISHED, 1",
        "1, REJECTED, 1"
    })
    public void ignoreUnfinishedOrUnrelatedTask(String transactionId, String taskKey, Integer iteration) {
        assertFalse(sagaLogRepository
            .findFinishLogTypeKeyAndIteration(transactionId, taskKey, iteration).isPresent());
    }

    @ParameterizedTest
    @CsvSource({"FINISHED, 3", "REJECTED, 6"})
    public void matchOnlyRequestedIteration(String taskKey, Long logId) {
        sagaLogRepository.findById(logId).orElseThrow().setIteration(2);
        entityManager.flush();
        entityManager.clear();

        assertEquals(logId, sagaLogRepository
            .findFinishLogTypeKeyAndIteration("1", taskKey, 2).orElseThrow().getId());
        assertFalse(sagaLogRepository.findFinishLogTypeKeyAndIteration("1", taskKey, 1).isPresent());
        assertFalse(sagaLogRepository.findFinishLogTypeKeyAndIteration("1", taskKey, null).isPresent());
    }

    private SagaLog sagaLog(String finished, long id, String key) {
        return sagaLog(finished, id, key, SagaLogType.EVENT_END);
    }

    private SagaLog sagaLog(String finished, long id, String key, SagaLogType sagaLogType) {
        return new SagaLog().setEventTypeKey(finished).setLogType(sagaLogType)
            .setSagaTransaction(new SagaTransaction().setId("1").setTypeKey("A").setKey(key)).setId(id);
    }

}
