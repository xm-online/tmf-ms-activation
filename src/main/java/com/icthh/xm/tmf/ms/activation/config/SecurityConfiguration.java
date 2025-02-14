package com.icthh.xm.tmf.ms.activation.config;

import com.icthh.xm.commons.permission.constants.RoleConstant;
import com.icthh.xm.commons.security.oauth2.ConfigSignatureVerifierClient;
import com.icthh.xm.commons.security.oauth2.OAuth2JwtAccessTokenConverter;
import com.icthh.xm.commons.security.oauth2.OAuth2Properties;
import com.icthh.xm.commons.security.oauth2.OAuth2SignatureVerifierClient;
import com.icthh.xm.tmf.ms.activation.config.ApplicationProperties.RestTemplateProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Configuration
@EnableResourceServer
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
@RequiredArgsConstructor
public class SecurityConfiguration extends ResourceServerConfigurerAdapter {

    private final OAuth2Properties oAuth2Properties;
    private final ApplicationProperties applicationProperties;

    @Value("${ribbon.http.client.enabled:true}")
    private Boolean ribbonTemplateEnabled;

    @Override
    public void configure(HttpSecurity http) throws Exception {
        http
            .csrf()
            .disable()
            .headers()
            .frameOptions()
            .disable()
            .and()
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
            .antMatchers("/api/**").authenticated()
            .antMatchers("/tmf-api/**").authenticated()
            .antMatchers("/DSEntityProvisioning/**").authenticated()
            .antMatchers("/management/health").permitAll()
            .antMatchers("/management/info").permitAll()
            .antMatchers("/management/prometheus/**").permitAll()
            .antMatchers("/management/**").hasAuthority(RoleConstant.SUPER_ADMIN)
            .antMatchers("/swagger-resources/configuration/ui").permitAll();
    }

    @Bean
    public TokenStore tokenStore(JwtAccessTokenConverter jwtAccessTokenConverter) {
        return new JwtTokenStore(jwtAccessTokenConverter);
    }

    @Bean
    public JwtAccessTokenConverter jwtAccessTokenConverter(OAuth2SignatureVerifierClient signatureVerifierClient) {
        return new OAuth2JwtAccessTokenConverter(oAuth2Properties, signatureVerifierClient);
    }

    @Bean
    @Qualifier("loadBalancedRestTemplate")
    public RestTemplate loadBalancedRestTemplate(ObjectProvider<RestTemplateCustomizer> customizerProvider) {
        HttpComponentsClientHttpRequestFactory httpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        RestTemplateProperties restTemplateProperties = applicationProperties.getLoadBalancedRestTemplate();
        httpRequestFactory.setConnectionRequestTimeout(restTemplateProperties.getConnectionRequestTimeout());
        httpRequestFactory.setConnectTimeout(restTemplateProperties.getConnectTimeout());
        httpRequestFactory.setReadTimeout(restTemplateProperties.getReadTimeout());

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(httpRequestFactory);

        if (ribbonTemplateEnabled) {
            log.info("loadBalancedRestTemplate: using Ribbon load balancer");
            customizerProvider.ifAvailable(customizer -> customizer.customize(restTemplate));
        }
        return restTemplate;
    }

    @Bean
    public ConfigSignatureVerifierClient configSignatureVerifierClient(@Qualifier("loadBalancedRestTemplate") RestTemplate restTemplate) {
        return new ConfigSignatureVerifierClient(oAuth2Properties, restTemplate);
    }

    @Bean
    @Qualifier("vanillaRestTemplate")
    public RestTemplate vanillaRestTemplate() {
        return new RestTemplate();
    }
}
