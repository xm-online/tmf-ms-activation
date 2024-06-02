package com.icthh.xm.tmf.ms.activation.config;


import com.icthh.xm.commons.config.client.service.TenantConfigService;
import com.icthh.xm.commons.domainevent.outbox.service.OutboxTransportService.OutboxTransportServiceField;
import com.icthh.xm.commons.domainevent.service.EventPublisher;
import com.icthh.xm.commons.domainevent.service.builder.DomainEventFactory;
import com.icthh.xm.commons.lep.api.BaseLepContext;
import com.icthh.xm.commons.lep.processor.GroovyMap;
import com.icthh.xm.commons.logging.trace.TraceService.TraceServiceField;
import com.icthh.xm.commons.topic.service.KafkaTemplateService;
import com.icthh.xm.tmf.ms.activation.config.TaskLepAdditionalContext.TaskContextField;
import com.icthh.xm.tmf.ms.activation.config.TransactionLepAdditionalContext.TransactionContextField;
import com.icthh.xm.tmf.ms.activation.service.MailService;
import com.icthh.xm.tmf.ms.activation.service.SagaService;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.RestTemplate;

@GroovyMap
public class LepContext extends BaseLepContext implements OutboxTransportServiceField, TraceServiceField, TransactionContextField, TaskContextField {

    public LepServices services;
    public LepTemplates templates;

    public ApplicationContext applicationContext;

    public static class LepServices {
        public TenantConfigService tenantConfigService;
        public MailService mailService;
        public SagaService sagaService;
        public EventPublisher eventPublisher;
        public DomainEventFactory domainEventFactory;
    }

    public static class LepTemplates {
        public KafkaTemplateService kafka;
        public RestTemplate rest;
    }

    @Override
    public String toString() {
        return "LepContext{[content hidden]}";
    }
}
