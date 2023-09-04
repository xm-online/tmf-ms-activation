package com.icthh.xm.tmf.ms.activation.config;

import com.icthh.xm.commons.config.client.repository.TenantListRepository;
import com.icthh.xm.commons.logging.util.MdcUtils;
import com.icthh.xm.commons.permission.inspector.PrivilegeInspector;
import com.icthh.xm.commons.tenant.TenantKey;
import com.icthh.xm.tmf.ms.activation.service.RetryService;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("unused")
public class ApplicationStartup implements ApplicationListener<ApplicationReadyEvent> {

    private final ApplicationProperties applicationProperties;
    private final PrivilegeInspector privilegeInspector;
    private final RetryService retryService;
    private final TenantListRepository tenantListRepository;
    private final TenantUtils tenantUtils;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        initPriveleges();
        rescheduleSagaEvents();
    }

    /**
     * Will reschedule all saga events.
     * In this method 'tenantUtils.doInTenantContext()' is used, which will destroy main thread tenant context,
     * to not break bean initializations that will be executed after this method, need to set up
     * main thread context again from tenant context before 'tenantUtils.doInTenantContext()' execution.
     */
    private void rescheduleSagaEvents() {
        Optional<TenantKey> tenantKeyBefore = tenantUtils.getOptionalTenantKey();
        tenantListRepository.getTenants().forEach(tenant ->
            tenantUtils.doInTenantContext(retryService::rescheduleAllEvents, tenant)
        );
        tenantKeyBefore.ifPresent(tenantUtils::setTenantKey);
    }

    private void initPriveleges() {
        if (applicationProperties.isKafkaEnabled()) {
            privilegeInspector.readPrivileges(MdcUtils.getRid());
        } else {
            log.warn("WARNING! Privileges inspection is disabled by "
                + "configuration parameter 'application.kafka-enabled'");
        }
    }
}
