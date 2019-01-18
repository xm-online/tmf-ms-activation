package com.icthh.xm.tmf.ms.activation.events.bindings;

import static com.icthh.xm.tmf.ms.activation.events.bindings.MessagingConfiguration.SAGA_EVENTS_PREFIX;

import java.util.Map;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

public class RoutingSerializer implements Serializer<Object> {

    private StringSerializer stringSerializer = new StringSerializer();
    private JsonSerializer<Object> sagaSerializer = new JsonSerializer<>();

    @Override
    public void configure(Map configs, boolean isKey) {
        stringSerializer.configure(configs, isKey);
        sagaSerializer.configure(configs, isKey);
    }

    @Override
    public byte[] serialize(String topic, Object data) {
        if (topic.startsWith(SAGA_EVENTS_PREFIX)) {
            return sagaSerializer.serialize(topic, data);
        }
        return stringSerializer.serialize(topic, String.valueOf(data));
    }

    @Override
    public void close() {
        stringSerializer.close();
        sagaSerializer.close();
    }
}
