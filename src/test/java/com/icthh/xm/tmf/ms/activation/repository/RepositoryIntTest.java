package com.icthh.xm.tmf.ms.activation.repository;

import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.CANCELED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.NEW;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

import com.github.database.rider.core.api.dataset.DataSet;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@Slf4j
@DataSet(value = "init-tx.xml")
public class RepositoryIntTest extends BaseDaoTest {

    @Autowired
    private SagaTransactionRepository sagaTransactionRepository;

    @Autowired
    private SagaEventRepository eventRepository;

    @Autowired
    private SagaLogRepository logRepository;

    @Test
    public void testDeleteNotExistsEvent() {
        eventRepository.delete(new SagaEvent().setId("-1").setTenantKey("XM").setTypeKey("TK").setTransactionId("1"));
    }

    @Test
    public void testFindNewSagaTransactions() {
        Page<SagaTransaction> page = sagaTransactionRepository.findAllBySagaTransactionState(NEW, PageRequest.of(0, 2));
        assertEquals(4, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
        assertEquals(asList(tx("1", "A", NEW, "2019-03-04T13:53:31"), tx("3", "A", NEW, "2019-03-04T13:49:57")), page.getContent());
    }

    @Test
    public void testFindAllNewSagaTransactions() {
        Page<SagaTransaction> page = sagaTransactionRepository.findAll(PageRequest.of(0, 2));
        assertEquals(5, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
        assertEquals(asList(tx("1", "A", NEW, "2019-03-04T13:53:31"), tx("2", "A", CANCELED, "2019-03-04T13:49:00")), page.getContent());
    }

    @Test
    public void testFindLogsByTransaction() {
        assertEquals(5, logRepository.findBySagaTransactionId("1").size());
    }

    @Test
    public void testCountOldTransaction() {
        assertEquals(2, sagaTransactionRepository.countByCreateDateBeforeAndSagaTransactionState(moveToSystemTime("2019-03-04T13:50:00"), NEW));
    }

    public SagaTransaction tx(String id, String typeKey, SagaTransactionState sagaTransactionState, String date) {
        return new SagaTransaction().setId(id).setKey(id).setTypeKey(typeKey).setSagaTransactionState(sagaTransactionState)
            .setCreateDate(moveToSystemTime(date));
    }

    private Instant moveToSystemTime(String date) {
        return LocalDateTime.parse(date).toInstant(
            ZoneId.of("UTC").getRules().getOffset(LocalDateTime.parse(date)));
    }
}
