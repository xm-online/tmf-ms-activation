package com.icthh.xm.tmf.ms.activation.config;

import static com.google.common.collect.ImmutableBiMap.of;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.ON_RETRY;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.SUSPENDED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.NEW;
import static java.time.Instant.now;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import com.ryantenney.metrics.spring.config.annotation.EnableMetrics;
import com.ryantenney.metrics.spring.config.annotation.MetricsConfigurerAdapter;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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
    private HikariDataSource hikariDataSource;

    public AppMetricsConfiguration(@Lazy SagaTransactionRepository sagaTransactionRepository,
                                   @Lazy SagaEventRepository sagaEventRepository, MetricRegistry metricRegistry,
                                   ApplicationProperties applicationProperties) {
        this.sagaTransactionRepository = sagaTransactionRepository;
        this.sagaEventRepository = sagaEventRepository;
        this.metricRegistry = metricRegistry;
        this.applicationProperties = applicationProperties;
    }

    @Autowired(required = false)
    public void setHikariDataSource(HikariDataSource hikariDataSource) {
        this.hikariDataSource = hikariDataSource;
    }

    @PostConstruct
    public void init() {
        if (hikariDataSource != null) {
            log.debug("Monitoring the datasource");
            // remove the factory created by HikariDataSourceMetricsPostProcessor until JHipster migrate to Micrometer
            hikariDataSource.setMetricsTrackerFactory(null);
            hikariDataSource.setMetricRegistry(metricRegistry);
        }

        Map<String, Metric> metrics = new HashMap<>();
        metrics.put("transactions.wait", createWailtTransactionMetric());
        metrics.put("transactions.all", (Gauge) sagaTransactionRepository::count);
        metrics.put("tasks.onretry", (Gauge) () -> sagaEventRepository.countByStatus(ON_RETRY));
        metrics.put("tasks.suspended", (Gauge) () -> {
            Instant date = now().minusSeconds(applicationProperties.getExpectedTransactionCompletionTimeSeconds());
            return sagaEventRepository.countByStatusAndCreateDateBefore(SUSPENDED, date);
        });

        metricRegistry.register("com.icthh.xm.tmf.ms.activation", (MetricSet) () -> metrics);
    }

    private Gauge createWailtTransactionMetric() {
        return () -> {
            Instant date = now().minusSeconds(applicationProperties.getExpectedTransactionCompletionTimeSeconds());
            return sagaTransactionRepository.countByCreateDateBeforeAndSagaTransactionState(date, NEW);
        };
    }
}
