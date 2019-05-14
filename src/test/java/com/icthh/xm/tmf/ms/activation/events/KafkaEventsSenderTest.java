package com.icthh.xm.tmf.ms.activation.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binding.BinderAwareChannelResolver;
import org.springframework.messaging.MessageChannel;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit4.SpringRunner;

@EnableRetry
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {KafkaEventsSender.class})
public class KafkaEventsSenderTest {

    @Autowired
    private EventsSender kafkaEventsSender;

    @MockBean
    private BinderAwareChannelResolver binderAwareChannelResolver;

    @Mock
    private MessageChannel messageChannel;

    @Before
    public void setUp() {
        when(messageChannel.send(any())).thenReturn(false)
                                        .thenReturn(false)
                                        .thenReturn(false)
                                        .thenReturn(true);
        when(binderAwareChannelResolver.resolveDestination(any())).thenReturn(messageChannel);
    }

    @Test
    public void testRetry() {
        this.kafkaEventsSender.sendEvent(new SagaEvent().setTenantKey("XM").setId("id"));
        verify(binderAwareChannelResolver, times(4)).resolveDestination(any());
        verify(messageChannel, times(4)).send(any());
    }
}
