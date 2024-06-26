package com.icthh.xm.tmf.ms.activation.config;

import com.icthh.xm.tmf.ms.activation.repository.SagaLogRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import com.icthh.xm.tmf.ms.activation.service.MapSpecResolver;
import com.icthh.xm.tmf.ms.activation.service.FinishTransactionStrategy;
import com.icthh.xm.tmf.ms.activation.service.SagaSpecResolver;
import com.icthh.xm.tmf.ms.activation.service.SagaTaskExecutor;
import com.icthh.xm.tmf.ms.activation.service.TransactionStatusStrategy;
import com.icthh.xm.tmf.ms.activation.utils.TransactionUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActivationConfiguration {

    @Bean
    @ConditionalOnMissingBean(TransactionStatusStrategy.class)
    public TransactionStatusStrategy transactionStatusStrategy(SagaTaskExecutor taskExecutor,
                                                               SagaTransactionRepository transactionRepository,
                                                               SagaLogRepository logRepository,
                                                               TransactionUtils transactionUtils) {
        return new FinishTransactionStrategy(taskExecutor, transactionRepository, logRepository, transactionUtils);
    }

    @Bean
    @ConditionalOnMissingBean(SagaSpecResolver.class)
    public SagaSpecResolver sagaSpecResolver() {
        return new MapSpecResolver();
    }

}
