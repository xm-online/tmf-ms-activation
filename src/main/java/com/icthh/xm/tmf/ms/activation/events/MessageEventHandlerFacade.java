package com.icthh.xm.tmf.ms.activation.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.icthh.xm.commons.logging.util.MdcUtils;
import com.icthh.xm.commons.topic.domain.TopicConfig;
import com.icthh.xm.commons.topic.message.MessageHandler;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.events.bindings.EventHandler;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;

import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.unwrap;

@Slf4j
public class MessageEventHandlerFacade implements MessageHandler {

    private static final String WRAP_TOKEN = "\"";

    private final EventHandler eventHandler;
    private final ObjectMapper objectMapper;

    public MessageEventHandlerFacade(EventHandler eventHandler) {
        this.eventHandler = eventHandler;
        this.objectMapper = initObjectMapper();
    }

    @Override
    public void onMessage(String message, String tenant, TopicConfig topicConfig) {
        try {
            MdcUtils.putRid(MdcUtils.generateRid() + ":" + tenant);
            final StopWatch stopWatch = StopWatch.createStarted();
            String payloadString = unwrap(message, WRAP_TOKEN);
            log.info("start processing message for tenant: [{}], base64 body = {}", tenant, payloadString);
            String eventBody = new String(Base64.getDecoder().decode(payloadString), UTF_8);
            log.info("start processing message for tenant: [{}], json body = {}", tenant, eventBody);

            eventHandler.onEvent(mapToEvent(eventBody), tenant);

            log.info("stop processing message for tenant: [{}], time = {}", tenant, stopWatch.getTime());

        } catch (Exception e) {
            log.error("error processing event for tenant [{}]", tenant, e);
            throw e;
        } finally {
            MdcUtils.removeRid();
        }
    }

    private ObjectMapper initObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @SneakyThrows
    private SagaEvent mapToEvent(String eventBody) {
        return objectMapper.readValue(eventBody, SagaEvent.class);
    }
}
