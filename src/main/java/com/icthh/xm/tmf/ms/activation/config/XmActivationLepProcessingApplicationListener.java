package com.icthh.xm.tmf.ms.activation.config;

import com.icthh.xm.commons.config.client.service.TenantConfigService;
import com.icthh.xm.commons.lep.commons.CommonsExecutor;
import com.icthh.xm.commons.lep.commons.CommonsService;
import com.icthh.xm.commons.lep.spring.SpringLepProcessingApplicationListener;
import com.icthh.xm.commons.mail.provider.MailProviderService;
import com.icthh.xm.lep.api.ScopedContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class XmActivationLepProcessingApplicationListener extends SpringLepProcessingApplicationListener {

    public static final String BINDING_KEY_COMMONS = "commons";
    public static final String BINDING_KEY_SERVICES = "services";
    public static final String BINDING_SUB_KEY_SERVICE_TENANT_CONFIG_SERVICE = "tenantConfigService";
    public static final String BINDING_KEY_TEMPLATES = "templates";
    public static final String BINDING_SUB_KEY_TEMPLATE_REST = "rest";
    public static final String BINDING_SUB_KEY_SERVICE_PROVIDER_MAIL = "mailProviderService";

    private final TenantConfigService tenantConfigService;
    private final RestTemplate restTemplate;
    private final CommonsService commonsService;
    private final ApplicationContext applicationContext;
    private final MailProviderService mailProviderService;


    public XmActivationLepProcessingApplicationListener(TenantConfigService tenantConfigService,
                                                        @Qualifier("loadBalancedRestTemplate") RestTemplate restTemplate,
                                                        CommonsService commonsService,
                                                        ApplicationContext applicationContext,
                                                        MailProviderService mailProviderService) {
        this.tenantConfigService = tenantConfigService;
        this.restTemplate = restTemplate;
        this.commonsService = commonsService;
        this.applicationContext = applicationContext;
        this.mailProviderService = mailProviderService;
    }

    @Override
    protected void bindExecutionContext(final ScopedContext executionContext) {
        // services
        Map<String, Object> services = new HashMap<>();
        services.put(BINDING_SUB_KEY_SERVICE_TENANT_CONFIG_SERVICE, tenantConfigService);
        executionContext.setValue(BINDING_KEY_COMMONS, new CommonsExecutor(commonsService));
        executionContext.setValue(BINDING_KEY_SERVICES, services);
        executionContext.setValue(BINDING_SUB_KEY_SERVICE_PROVIDER_MAIL, mailProviderService);
        // templates
        Map<String, Object> templates = new HashMap<>();
        templates.put(BINDING_SUB_KEY_TEMPLATE_REST, restTemplate);
        executionContext.setValue(BINDING_KEY_TEMPLATES, templates);
        executionContext.setValue("applicationContext", applicationContext);
    }
}
