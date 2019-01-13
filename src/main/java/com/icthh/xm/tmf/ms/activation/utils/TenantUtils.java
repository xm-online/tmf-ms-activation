package com.icthh.xm.tmf.ms.activation.utils;

import com.icthh.xm.commons.exceptions.BusinessException;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantUtils {

    private final TenantContextHolder tenantContextHolder;

    public String getTenantKey() {
        return tenantContextHolder.getContext().getTenantKey()
            .orElseThrow(() -> new BusinessException("tenant.not.found", "Tenant not exists."))
            .getValue();
    }

}
