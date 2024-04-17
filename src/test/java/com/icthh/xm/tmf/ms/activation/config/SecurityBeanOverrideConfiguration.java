package com.icthh.xm.tmf.ms.activation.config;

import static org.mockito.Mockito.mock;

import javax.annotation.PostConstruct;

import com.icthh.xm.commons.lep.spring.LepUpdateMode;
import com.icthh.xm.commons.logging.config.LoggingConfigService;
import com.icthh.xm.commons.logging.config.LoggingConfigServiceStub;
import org.springframework.cloud.client.loadbalancer.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.web.client.RestTemplate;

/**
 * Overrides UAA specific beans, so they do not interfere the testing
 * This configuration must be included in @SpringBootTest in order to take effect.
 */
@Configuration
@Profile("!dao-test")
public class SecurityBeanOverrideConfiguration {

    @Bean
    @Primary
    public TokenStore tokenStore() {
        return mock(TokenStore.class);
    }

    @Bean
    @Primary
    public JwtAccessTokenConverter jwtAccessTokenConverter() {
        return mock(JwtAccessTokenConverter.class);
    }

    @Bean
    @Primary
    public RestTemplate loadBalancedRestTemplate(RestTemplateCustomizer customizer) {
        return mock(RestTemplate.class);
    }

    @Bean
    @Primary
    public LepUpdateMode lepUpdateMode() {
        return LepUpdateMode.SYNCHRONOUS;
    }

    @PostConstruct
    public void postConstruct(){
        // Need system error to see when contect is recreated during dunning test from gradle
        System.err.println(" ===================== SecurityBeanOverrideConfiguration inited !!! =======================");
    }

}
