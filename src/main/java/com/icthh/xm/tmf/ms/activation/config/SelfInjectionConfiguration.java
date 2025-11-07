package com.icthh.xm.tmf.ms.activation.config;

import com.icthh.xm.tmf.ms.activation.events.KafkaEventsSender;
import com.icthh.xm.tmf.ms.activation.service.RetryService;
import com.icthh.xm.tmf.ms.activation.service.SagaServiceImpl;
import com.icthh.xm.tmf.ms.activation.service.TxFinishEventPublisher;
import com.icthh.xm.tmf.ms.activation.utils.LazyObjectProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SelfInjectionConfiguration {

    @Bean
    public TxFinishEventPublisher txFinishEventPublisher(ApplicationContext applicationContext) {
        return applicationContext::publishEvent;
    }

    @Bean
    public LazyObjectProvider<SagaServiceImpl> sagaServiceProvider(ObjectProvider<SagaServiceImpl> sagaServiceProvider) {
        return new LazyObjectProvider<>(sagaServiceProvider);
    }

    @Bean
    public LazyObjectProvider<RetryService> retryServiceProvider(ObjectProvider<RetryService> retryServiceProvider) {
        return new LazyObjectProvider<>(retryServiceProvider);
    }

    @Bean
    public LazyObjectProvider<KafkaEventsSender> kafkaEventsSenderProvider(ObjectProvider<KafkaEventsSender> kafkaEventsSenderProvider) {
        return new LazyObjectProvider<>(kafkaEventsSenderProvider);
    }

}
