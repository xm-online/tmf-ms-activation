package com.icthh.xm.tmf.ms.activation.config;

import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.ON_RETRY;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.SUSPENDED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.NEW;
import static java.time.Instant.now;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.icthh.xm.commons.config.client.repository.TenantListRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import com.ryantenney.metrics.spring.config.annotation.EnableMetrics;
import com.ryantenney.metrics.spring.config.annotation.MetricsConfigurerAdapter;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@EnableMetrics(proxyTargetClass = true)
public class AppMetricsConfiguration extends MetricsConfigurerAdapter {

    private final Logger log = LoggerFactory.getLogger(AppMetricsConfiguration.class);

    private final SagaTransactionRepository sagaTransactionRepository;
    private final SagaEventRepository sagaEventRepository;
    private final MetricRegistry metricRegistry;
    private final ApplicationProperties applicationProperties;
    private final KafkaOffsetsMetric kafkaOffsetsMetric;
    private final TenantListRepository tenantListRepository;
    private final TenantUtils tenantUtils;

    private HikariDataSource hikariDataSource;

    public AppMetricsConfiguration(@Lazy SagaTransactionRepository sagaTransactionRepository,
                                   @Lazy SagaEventRepository sagaEventRepository, MetricRegistry metricRegistry,
                                   TenantListRepository tenantListRepository,
                                   ApplicationProperties applicationProperties, KafkaOffsetsMetric kafkaOffsetsMetric,
                                   TenantUtils tenantUtils) {
        this.sagaTransactionRepository = sagaTransactionRepository;
        this.sagaEventRepository = sagaEventRepository;
        this.metricRegistry = metricRegistry;
        this.applicationProperties = applicationProperties;
        this.kafkaOffsetsMetric = kafkaOffsetsMetric;
        this.tenantListRepository = tenantListRepository;
        this.tenantUtils = tenantUtils;
    }

    @Autowired(required = false)
    public void setHikariDataSource(HikariDataSource hikariDataSource) {
        this.hikariDataSource = hikariDataSource;
    }

    @PostConstruct
    public void init() {
        metricRegistry.register("spring.cloud.stream.binder", kafkaOffsetsMetric);

        if (hikariDataSource != null) {
            log.debug("Monitoring the datasource");
            // remove the factory created by HikariDataSourceMetricsPostProcessor until JHipster migrate to Micrometer
            hikariDataSource.setMetricsTrackerFactory(null);
            hikariDataSource.setMetricRegistry(metricRegistry);
        }

        Map<String, Metric> metrics = new HashMap<>();
        tenantListRepository.getTenants().stream().map(String::toUpperCase).forEach(tenant -> {
            metrics.put("transactions.wait." + tenant, inTenant(this::getWaitTransactionsCount, tenant));
            metrics.put("transactions.all." + tenant, inTenant(sagaTransactionRepository::count, tenant));
            metrics.put("tasks.onretry." + tenant, inTenant(() -> sagaEventRepository.countByStatus(ON_RETRY), tenant));
            metrics.put("tasks.suspended." + tenant, inTenant(this::getCountSuspendedTasks, tenant));
        });
        metricRegistry.register("com.icthh.xm.tmf.ms.activation", (MetricSet) () -> metrics);
    }

    private Long getCountSuspendedTasks() {
        Instant date = now().minusSeconds(applicationProperties.getExpectedTransactionCompletionTimeSeconds());
        return sagaEventRepository.countByStatusAndCreateDateBefore(SUSPENDED, date);
    }

    private long getWaitTransactionsCount() {
        Instant date = now().minusSeconds(applicationProperties.getExpectedTransactionCompletionTimeSeconds());
        return sagaTransactionRepository.countByCreateDateBeforeAndSagaTransactionState(date, NEW);
    }

    private Gauge inTenant(Supplier<Long> metricSupplier, String tenant) {
        return () -> tenantUtils.doInTenantContext(metricSupplier::get, tenant);
    }
}
