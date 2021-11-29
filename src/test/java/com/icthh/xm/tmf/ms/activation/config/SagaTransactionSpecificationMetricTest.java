package com.icthh.xm.tmf.ms.activation.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class SagaTransactionSpecificationMetricTest {

    @Mock
    private SagaTransactionRepository sagaTransactionRepository;
    @Mock
    private ApplicationProperties applicationProperties;
    @Mock
    private TenantUtils tenantUtils;
    @Spy
    private MetricRegistry metricRegistry;

    @Test
    public void shouldCreateTransactionMetrics() {
        // GIVEN
        SagaTransactionSpecificationMetric sagaTransactionSpecificationMetric =
            new SagaTransactionSpecificationMetric(sagaTransactionRepository, applicationProperties,
                tenantUtils, metricRegistry);
        metricRegistry.meter("com.icthh.xm.tmf.ms.activation.specification");

        // WHEN
        sagaTransactionSpecificationMetric.initMetrics("XM", List.of("SPEC-1", "SPEC-2"));

        // THEN
        Map<String, Metric> metrics = metricRegistry.getMetrics();
        assertEquals(4, metrics.size());
        assertTrue(metrics.containsKey("com.icthh.xm.tmf.ms.activation.specification.transactions.wait.XM.SPEC-2"));
        assertTrue(metrics.containsKey("com.icthh.xm.tmf.ms.activation.specification.transactions.suspended.XM.SPEC-2"));
        assertTrue(metrics.containsKey("com.icthh.xm.tmf.ms.activation.specification.transactions.wait.XM.SPEC-1"));
        assertTrue(metrics.containsKey("com.icthh.xm.tmf.ms.activation.specification.transactions.suspended.XM.SPEC-1"));
        assertTrue(metrics.get("com.icthh.xm.tmf.ms.activation.specification.transactions.wait.XM.SPEC-2") instanceof Gauge);
    }

}
