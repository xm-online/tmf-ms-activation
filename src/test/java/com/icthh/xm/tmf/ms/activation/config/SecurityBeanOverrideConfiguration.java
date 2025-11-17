package com.icthh.xm.tmf.ms.activation.config;

import static com.icthh.xm.commons.config.client.config.XmRestTemplateConfiguration.XM_CONFIG_REST_TEMPLATE;
import static org.mockito.Mockito.mock;

import com.icthh.xm.commons.config.client.service.TenantAliasServiceImpl;
import com.icthh.xm.commons.security.jwt.TokenProvider;
import com.icthh.xm.commons.security.oauth2.JwtVerificationKeyClient;
import jakarta.annotation.PostConstruct;

import com.icthh.xm.commons.config.client.repository.CommonConfigRepository;
import com.icthh.xm.commons.config.client.repository.TenantListRepository;
import com.icthh.xm.commons.config.client.service.TenantAliasService;
import com.icthh.xm.commons.lep.spring.LepUpdateMode;
import lombok.SneakyThrows;
import org.springframework.cloud.client.loadbalancer.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
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

    @Bean
    @Primary
    // TODO remove implementation
    public JwtVerificationKeyClient jwtVerificationKeyClient() {
        return () -> readFile("config/public.cer");
    }

    @Bean
    @Primary
    public TokenProvider tokenProvider() {
        return mock(TokenProvider.class);
    }

    @Bean(XM_CONFIG_REST_TEMPLATE)
    public RestTemplate restTemplate(RestTemplateCustomizer customizer) {
        return mock(RestTemplate.class);
    }

    @SneakyThrows
    public static byte[] readFile(String path) {
        return SecurityBeanOverrideConfiguration.class.getClassLoader().getResourceAsStream(path).readAllBytes();
    }

    @Bean
    public TenantAliasService tenantAliasService() {
        return new TenantAliasServiceImpl(mock(CommonConfigRepository.class), mock(TenantListRepository.class));
    }

}
