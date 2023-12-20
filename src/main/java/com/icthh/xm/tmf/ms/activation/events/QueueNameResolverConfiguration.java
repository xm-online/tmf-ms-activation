package com.icthh.xm.tmf.ms.activation.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueueNameResolverConfiguration {

    @Bean
    @ConditionalOnMissingBean(QueueNameResolver.class)
    public QueueNameResolver tenantQueueNameResolver() {
        return new TenantQueueNameResolver();
    }
}
