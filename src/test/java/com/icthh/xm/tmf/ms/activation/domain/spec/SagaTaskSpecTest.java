package com.icthh.xm.tmf.ms.activation.domain.spec;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SagaTaskSpecTest {

    private static final Integer TEN_INT = 10;
    private static final Long TEN_LONG = 10L;
    private static final RetryPolicy ROLLBACK_RETRY_POLICY = RetryPolicy.ROLLBACK;

    private static final Long DEFAULT_RETRY_COUNT = -1L;
    private static final Integer DEFAULT_BACK_OFF = 5;
    private static final Integer DEFAULT_MAX_BACK_OFF = 30;
    private static final RetryPolicy DEFAULT_RETRY_POLICY = RetryPolicy.RETRY;

    @Test
    public void setTransactionConfig() {
        SagaTransactionSpec transactionSpec = new SagaTransactionSpec();
        transactionSpec.setMaxBackOff(TEN_INT);
        transactionSpec.setBackOff(TEN_INT);
        transactionSpec.setRetryCount(TEN_LONG);
        transactionSpec.setRetryPolicy(ROLLBACK_RETRY_POLICY);
        SagaTaskSpec sagaTaskSpec = new SagaTaskSpec();
        sagaTaskSpec.applyAsDefaultTransactionConfig(transactionSpec);
        assertEquals(sagaTaskSpec.getMaxBackOff(), transactionSpec.getMaxBackOff());
        assertEquals(sagaTaskSpec.getBackOff(), transactionSpec.getBackOff());
        assertEquals(sagaTaskSpec.getRetryCount(), transactionSpec.getRetryCount());
        assertEquals(sagaTaskSpec.getRetryPolicy(), transactionSpec.getRetryPolicy());
    }

    @Test
    public void setTransactionConfigByDefault() {
        SagaTransactionSpec transactionSpec = new SagaTransactionSpec();
        transactionSpec.setMaxBackOff(null);
        transactionSpec.setBackOff(null);
        transactionSpec.setRetryCount(null);
        transactionSpec.setRetryPolicy(null);
        SagaTaskSpec sagaTaskSpec = new SagaTaskSpec();
        sagaTaskSpec.applyAsDefaultTransactionConfig(transactionSpec);
        assertEquals(DEFAULT_MAX_BACK_OFF, sagaTaskSpec.getMaxBackOff());
        assertEquals(DEFAULT_BACK_OFF, sagaTaskSpec.getBackOff());
        assertEquals(DEFAULT_RETRY_COUNT, sagaTaskSpec.getRetryCount());
        assertEquals(DEFAULT_RETRY_POLICY, sagaTaskSpec.getRetryPolicy());
    }

    @Test
    public void setBackOffAndRetryCountByDefault() {
        SagaTransactionSpec transactionSpec = new SagaTransactionSpec();
        transactionSpec.setMaxBackOff(TEN_INT);
        transactionSpec.setBackOff(null);
        transactionSpec.setRetryCount(null);
        transactionSpec.setRetryPolicy(ROLLBACK_RETRY_POLICY);
        SagaTaskSpec sagaTaskSpec = new SagaTaskSpec();
        sagaTaskSpec.applyAsDefaultTransactionConfig(transactionSpec);
        assertEquals(sagaTaskSpec.getMaxBackOff(), transactionSpec.getMaxBackOff());
        assertEquals(DEFAULT_BACK_OFF, sagaTaskSpec.getBackOff());
        assertEquals(DEFAULT_RETRY_COUNT, sagaTaskSpec.getRetryCount());
        assertEquals(sagaTaskSpec.getRetryPolicy(), transactionSpec.getRetryPolicy());
    }

}
