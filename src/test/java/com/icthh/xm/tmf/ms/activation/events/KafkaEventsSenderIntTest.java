package com.icthh.xm.tmf.ms.activation.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.icthh.xm.commons.exceptions.BusinessException;
import com.icthh.xm.tmf.ms.activation.config.SelfInjectionConfiguration;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.UUID;

@EnableRetry
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {KafkaEventsSender.class, KafkaTransport.class, QueueNameResolverConfiguration.class, SelfInjectionConfiguration.class})
@EnableTransactionManagement(proxyTargetClass = true)
public class KafkaEventsSenderIntTest {

    @Autowired
    private EventsSender kafkaEventsSender;

    @MockitoBean
    private ObjectMapper objectMapper;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    public void setUp() {
        when(kafkaTemplate.send(any(), any(), any(), any()))
            .thenThrow(new BusinessException("First failure"))
            .thenThrow(new BusinessException("Second failure"))
            .thenThrow(new BusinessException("Third failure"))
            .thenReturn(null);
    }

    @Test
    public void testRetry() {
        SagaEvent sagaEvent = new SagaEvent().setTenantKey("XM").setId("id").setTransactionId(UUID.randomUUID().toString());

        when(objectMapper.writeValueAsString(any())).thenReturn(sagaEvent.toString());

        this.kafkaEventsSender.sendEvent("anyTxKey", sagaEvent);

        verify(kafkaTemplate, times(4)).send(any(), any(), any(), any());
    }
}
