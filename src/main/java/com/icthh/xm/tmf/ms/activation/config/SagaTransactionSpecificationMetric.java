package com.icthh.xm.tmf.ms.activation.config;

import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.SUSPENDED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.NEW;
import static java.time.Instant.now;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SagaTransactionSpecificationMetric {

    private static final String METRIC_GROUP_NAME = "com.icthh.xm.tmf.ms.activation.specification";
    private final SagaTransactionRepository sagaTransactionRepository;
    private final ApplicationProperties applicationProperties;
    private final TenantUtils tenantUtils;
    private final MetricRegistry metricRegistry;

    public SagaTransactionSpecificationMetric(@Lazy SagaTransactionRepository sagaTransactionRepository,
                                              ApplicationProperties applicationProperties,
                                              TenantUtils tenantUtils,
                                              MetricRegistry metricRegistry) {
        this.sagaTransactionRepository = sagaTransactionRepository;
        this.applicationProperties = applicationProperties;
        this.tenantUtils = tenantUtils;
        this.metricRegistry = metricRegistry;
    }

    public void initMetrics(String tenant, List<String> specificationKeys) {
        Boolean metricsEnabled = applicationProperties.getCustomMetrics().getSagaTransactionSpecificationMetricsEnabled();
        if (metricsEnabled) {
            log.info("initMetrics: tenant: {}, specificationKeys: {}", tenant, specificationKeys);
            metricRegistry.removeMatching((name, metric) -> name.startsWith(METRIC_GROUP_NAME));
            metricRegistry.register(METRIC_GROUP_NAME, (MetricSet) () -> {
                Map<String, Metric> metrics = new HashMap<>();
                specificationKeys.forEach(specKey -> {
                    metrics.put("transactions.wait." + tenant + "." + specKey,
                        inTenant(() -> this.getWaitTransactionsCount(specKey), tenant));
                    metrics.put("transactions.suspended." + tenant + "." + specKey,
                        inTenant(() -> this.getTransactionsCountWithSuspendedTasks(specKey), tenant));
                });

                return metrics;
            });
        } else {
            log.info("initMetrics: metrics disabled via configuration property " +
                "application.custom-metrics.saga-transaction-specification-metrics-enabled");
        }
    }

    private Gauge inTenant(Supplier<Long> metricSupplier, String tenant) {
        return () -> tenantUtils.doInTenantContext(metricSupplier::get, tenant);
    }

    private long getWaitTransactionsCount(String typeKey) {
        Instant date = now().minusSeconds(applicationProperties.getExpectedTransactionCompletionTimeSeconds());
        return sagaTransactionRepository.countByCreateDateBeforeAndSagaTransactionStateAndTypeKey(date, NEW, typeKey);
    }

    private long getTransactionsCountWithSuspendedTasks(String typeKey) {
        Instant date = now().minusSeconds(applicationProperties.getExpectedTransactionCompletionTimeSeconds());
        return sagaTransactionRepository.countByTypeKeyAndEventStatusAndCreatedDateBefore(typeKey, SUSPENDED, date);
    }
}
