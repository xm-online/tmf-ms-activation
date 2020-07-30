package com.icthh.xm.tmf.ms.activation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

public class SagaTransactionFactoryTest {

    private SagaTransactionFactory sagaTransactionFactory;

    @Before
    public void setUp() {
        sagaTransactionFactory = new SagaTransactionFactory();
    }

    @Test
    public void testCreateSagaTransaction() {
        String expectedTypeKey = "SOME_SERVICE";
        String expectedKey = UUID.randomUUID().toString();
        Map<String, Object> expectedContext = new HashMap<>();
        expectedContext.put("msisdn", "380764563728");
        expectedContext.put("key", expectedKey);

        SagaTransaction sagaTransaction =
            sagaTransactionFactory.createSagaTransaction(expectedTypeKey, expectedContext);

        assertEquals(expectedKey, sagaTransaction.getKey());
        assertEquals(expectedTypeKey, sagaTransaction.getTypeKey());
        assertEquals(expectedContext, sagaTransaction.getContext());
    }
}