package com.icthh.xm.tmf.ms.activation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.icthh.xm.tmf.ms.activation.AbstractUnitTest;
import com.icthh.xm.tmf.ms.activation.config.ApplicationProperties;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class SagaTransactionFactoryUnitTest extends AbstractUnitTest {

    private ApplicationProperties applicationProperties;
    private SagaTransactionFactory sagaTransactionFactory;

    @BeforeEach
    public void setUp() {
        applicationProperties = new ApplicationProperties();
        sagaTransactionFactory = new SagaTransactionFactory(applicationProperties);
    }

    @AfterEach
    public void tearDown() {
        RequestContextHolder.resetRequestAttributes();
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

    @Test
    public void testExcludedHeadersAreNotCopiedToContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        request.addHeader("Cookie", "session=1");
        request.addHeader("x-request-id", "req-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        SagaTransaction sagaTransaction =
            sagaTransactionFactory.createSagaTransaction("SOME_SERVICE", Map.of("msisdn", "380764563728"));

        assertEquals(Map.of("msisdn", "380764563728", "x-request-id", "req-1"), sagaTransaction.getContext());
    }

    @Test
    public void testExcludedHeadersAreConfigurable() {
        applicationProperties.setSagaContextExcludedHeaders(List.of("x-request-id"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        request.addHeader("x-request-id", "req-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        SagaTransaction sagaTransaction = sagaTransactionFactory.createSagaTransaction("SOME_SERVICE", Map.of());

        assertEquals(Map.of("Authorization", "Bearer token"), sagaTransaction.getContext());
    }
}
