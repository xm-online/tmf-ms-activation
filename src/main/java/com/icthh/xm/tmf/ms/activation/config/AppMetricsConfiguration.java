package com.icthh.xm.tmf.ms.activation.config;

import com.codahale.metrics.MetricRegistry;
import com.ryantenney.metrics.spring.config.annotation.EnableMetrics;
import com.ryantenney.metrics.spring.config.annotation.MetricsConfigurerAdapter;
import com.zaxxer.hikari.HikariDataSource;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableMetrics(proxyTargetClass = true)
public class AppMetricsConfiguration extends MetricsConfigurerAdapter {

    private final Logger log = LoggerFactory.getLogger(AppMetricsConfiguration.class);

    private final MetricRegistry metricRegistry;
    private final ActivationKafkaOffsetsMetric kafkaOffsetsMetric;

    private HikariDataSource hikariDataSource;

    public AppMetricsConfiguration(MetricRegistry metricRegistry,
                                   ActivationKafkaOffsetsMetric kafkaOffsetsMetric) {
        this.metricRegistry = metricRegistry;
        this.kafkaOffsetsMetric = kafkaOffsetsMetric;
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
    }
}
