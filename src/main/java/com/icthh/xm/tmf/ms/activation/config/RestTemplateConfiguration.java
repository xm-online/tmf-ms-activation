package com.icthh.xm.tmf.ms.activation.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Configuration
public class RestTemplateConfiguration {

    @Value("${ribbon.http.client.enabled:true}")
    private Boolean loadBalancerEnabled;

    @Bean
    @Qualifier("loadBalancedRestTemplate")
    public RestTemplate loadBalancedRestTemplate(ObjectProvider<RestTemplateCustomizer> customizerProvider) {
        RestTemplate restTemplate = new RestTemplate();
        if (loadBalancerEnabled) {
            customizerProvider.ifAvailable(customizer -> {
                log.info("loadBalancedRestTemplate: using Spring Cloud LoadBalancer");
                customizer.customize(restTemplate);
            });
        }
        return restTemplate;
    }


    @Bean
    @Qualifier("vanillaRestTemplate")
    public RestTemplate vanillaRestTemplate() {
        return new RestTemplate();
    }
}
