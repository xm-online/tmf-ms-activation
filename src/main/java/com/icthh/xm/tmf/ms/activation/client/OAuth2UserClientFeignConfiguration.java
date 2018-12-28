package com.icthh.xm.tmf.ms.activation.client;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

public class OAuth2UserClientFeignConfiguration {

    @Bean(name = "userFeignClientInterceptor")
    public RequestInterceptor getUserFeignClientInterceptor() throws IOException {
        return new UserFeignClientInterceptor();
    }
}
