package com.icthh.xm.tmf.ms.activation.config;

import com.icthh.xm.commons.config.client.service.TenantConfigService;
import com.icthh.xm.commons.lep.BaseProceedingLep;
import com.icthh.xm.commons.lep.commons.CommonsService;
import com.icthh.xm.commons.lep.spring.LepThreadHelper;
import com.icthh.xm.commons.security.XmAuthenticationContext;
import com.icthh.xm.commons.tenant.TenantContext;
import com.icthh.xm.commons.topic.service.KafkaTemplateService;
import com.icthh.xm.tmf.ms.activation.service.MailService;
import com.icthh.xm.tmf.ms.activation.service.SagaService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.RestTemplate;

public class LepContext {

    public Object commons;
    public Object inArgs;
    public BaseProceedingLep lep;
    public LepThreadHelper thread;
    public XmAuthenticationContext authContext;
    public TenantContext tenantContext;
    public Object methodResult;

    public LepServices services;
    public LepTemplates templates;

    public ApplicationContext applicationContext;

    public static class LepServices {
        public TenantConfigService tenantConfigService;
        public MailService mailService;
        public SagaService sagaService;
    }

    public static class LepTemplates {
        public KafkaTemplateService kafka;
        public RestTemplate rest;
    }


}
