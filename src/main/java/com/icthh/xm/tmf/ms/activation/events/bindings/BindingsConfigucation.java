package com.icthh.xm.tmf.ms.activation.events.bindings;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableBinding(MessageChannels.class)
public class BindingsConfigucation {
}
