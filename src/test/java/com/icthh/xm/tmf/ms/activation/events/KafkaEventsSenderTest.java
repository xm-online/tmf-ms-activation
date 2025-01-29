package com.icthh.xm.tmf.ms.activation.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icthh.xm.commons.exceptions.BusinessException;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.UUID;

@EnableRetry
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {KafkaEventsSender.class, QueueNameResolverConfiguration.class})
@EnableTransactionManagement(proxyTargetClass = true)
public class KafkaEventsSenderTest {

    @Autowired
    private EventsSender kafkaEventsSender;

    @MockBean
    private ObjectMapper objectMapper;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Before
    public void setUp() {
        when(kafkaTemplate.send(any(), any(), any(), any()))
            .thenThrow(new BusinessException("First failure"))
            .thenThrow(new BusinessException("Second failure"))
            .thenThrow(new BusinessException("Third failure"))
            .thenReturn(null);
    }

    @Test
    public void testRetry() throws JsonProcessingException {
        SagaEvent sagaEvent = new SagaEvent().setTenantKey("XM").setId("id").setTransactionId(UUID.randomUUID().toString());

        when(objectMapper.writeValueAsString(any())).thenReturn(sagaEvent.toString());

        this.kafkaEventsSender.sendEvent(sagaEvent);

        verify(kafkaTemplate, times(4)).send(any(), any(), any(), any());
    }
}
