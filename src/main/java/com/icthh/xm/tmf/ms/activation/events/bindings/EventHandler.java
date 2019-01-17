package com.icthh.xm.tmf.ms.activation.events.bindings;

import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_AUTH_CONTEXT;
import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_TENANT_CONTEXT;

import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.lep.api.LepManager;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.service.SagaService;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventHandler {

    private final SagaService sagaService;
    private final TenantUtils tenantUtils;

    public void onEvent(SagaEvent sagaEvent, String tenant) {
        tenantUtils.doInTenantContext(() -> {
            log.info("Receive event {} {}", sagaEvent, tenant);
            sagaService.onSagaEvent(sagaEvent);
        }, tenant);
    }


}
