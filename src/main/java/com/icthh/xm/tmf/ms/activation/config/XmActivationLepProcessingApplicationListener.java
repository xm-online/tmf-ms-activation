package com.icthh.xm.tmf.ms.activation.config;

import com.icthh.xm.commons.config.client.service.TenantConfigService;
import com.icthh.xm.commons.lep.commons.CommonsExecutor;
import com.icthh.xm.commons.lep.commons.CommonsService;
import com.icthh.xm.commons.lep.spring.SpringLepProcessingApplicationListener;
import com.icthh.xm.lep.api.ScopedContext;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class XmActivationLepProcessingApplicationListener extends SpringLepProcessingApplicationListener {

    public static final String BINDING_KEY_COMMONS = "commons";
    public static final String BINDING_KEY_SERVICES = "services";
    public static final String BINDING_SUB_KEY_SERVICE_TENANT_CONFIG_SERVICE = "tenantConfigService";
    public static final String BINDING_KEY_TEMPLATES = "templates";
    public static final String BINDING_SUB_KEY_TEMPLATE_REST = "rest";

    private final TenantConfigService tenantConfigService;
    private final RestTemplate restTemplate;
    private final CommonsService commonsService;

    public XmActivationLepProcessingApplicationListener(TenantConfigService tenantConfigService,
                                                        @Qualifier("loadBalancedRestTemplate") RestTemplate restTemplate,
                                                        CommonsService commonsService) {
        this.tenantConfigService = tenantConfigService;
        this.restTemplate = restTemplate;
        this.commonsService = commonsService;
    }

    @Override
    protected void bindExecutionContext(final ScopedContext executionContext) {
        // services
        Map<String, Object> services = new HashMap<>();
        services.put(BINDING_SUB_KEY_SERVICE_TENANT_CONFIG_SERVICE, tenantConfigService);
        executionContext.setValue(BINDING_KEY_COMMONS, new CommonsExecutor(commonsService));
        executionContext.setValue(BINDING_KEY_SERVICES, services);
        // templates
        Map<String, Object> templates = new HashMap<>();
        templates.put(BINDING_SUB_KEY_TEMPLATE_REST, restTemplate);
        executionContext.setValue(BINDING_KEY_TEMPLATES, templates);
    }
}
