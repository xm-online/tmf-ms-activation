package com.icthh.xm.tmf.ms.activation.domain.spec;


import org.junit.Test;

import java.util.List;

public class SagaSpecTest {

    @Test
    public void test() {

        SagaSpec s1 = new SagaSpec();
        s1.setTransactions(List.of(tx("key1"), tx("key3")));
        SagaSpec s2 = new SagaSpec();
        s2.setTransactions(List.of(tx("key2"), tx("key4")));

        List.of(s1, s1).stream().reduce(SagaSpec::mergeSpec).ifPresent(System.out::println);
    }

    private static SagaTransactionSpec tx(String key) {
        SagaTransactionSpec sagaTransactionSpec = new SagaTransactionSpec();
        sagaTransactionSpec.setKey(key);
        return sagaTransactionSpec;
    }

}