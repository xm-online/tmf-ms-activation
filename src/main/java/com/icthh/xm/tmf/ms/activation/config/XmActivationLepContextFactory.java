package com.icthh.xm.tmf.ms.activation.config;

import com.icthh.xm.commons.config.client.service.TenantConfigService;
import com.icthh.xm.commons.domainevent.service.EventPublisher;
import com.icthh.xm.commons.domainevent.service.builder.DomainEventFactory;
import com.icthh.xm.commons.lep.api.BaseLepContext;
import com.icthh.xm.commons.lep.api.LepContextFactory;
import com.icthh.xm.commons.lep.commons.CommonsService;
import com.icthh.xm.commons.topic.service.KafkaTemplateService;
import com.icthh.xm.lep.api.LepMethod;
import com.icthh.xm.tmf.ms.activation.service.MailService;
import com.icthh.xm.tmf.ms.activation.service.SagaService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class XmActivationLepContextFactory implements LepContextFactory {

    private final TenantConfigService tenantConfigService;
    private final RestTemplate restTemplate;
    private final ApplicationContext applicationContext;
    private final MailService mailService;
    private final SagaService sagaService;
    private final KafkaTemplateService kafkaTemplateService;
    private final EventPublisher eventPublisher;
    private final DomainEventFactory domainEventFactory;

    public XmActivationLepContextFactory(TenantConfigService tenantConfigService,
                                         @Qualifier("loadBalancedRestTemplate") RestTemplate restTemplate,
                                         ApplicationContext applicationContext,
                                         MailService mailService,
                                         SagaService sagaService,
                                         KafkaTemplateService kafkaTemplateService,
                                         EventPublisher eventPublisher,
                                         DomainEventFactory domainEventFactory) {
        this.tenantConfigService = tenantConfigService;
        this.restTemplate = restTemplate;
        this.applicationContext = applicationContext;
        this.mailService = mailService;
        this.sagaService = sagaService;
        this.kafkaTemplateService = kafkaTemplateService;
        this.eventPublisher = eventPublisher;
        this.domainEventFactory = domainEventFactory;
    }

    @Override
    public BaseLepContext buildLepContext(LepMethod lepMethod) {
        LepContext lepContext = new LepContext();
        lepContext.services = new LepContext.LepServices();
        lepContext.services.tenantConfigService = tenantConfigService;
        lepContext.services.mailService = mailService;
        lepContext.services.sagaService = sagaService;
        lepContext.services.eventPublisher = eventPublisher;
        lepContext.services.domainEventFactory = domainEventFactory;
        lepContext.templates = new LepContext.LepTemplates();
        lepContext.templates.kafka = kafkaTemplateService;
        lepContext.templates.rest = restTemplate;
        lepContext.applicationContext = applicationContext;
        return lepContext;
    }
}
