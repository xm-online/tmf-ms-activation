package com.icthh.xm.tmf.ms.activation.utils;

import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_AUTH_CONTEXT;
import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_TENANT_CONTEXT;

import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.lep.api.LepManager;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantUtils {

    private final TenantContextHolder tenantContextHolder;
    private final XmAuthenticationContextHolder authContextHolder;
    private final LepManager lepManager;

    public String getTenantKey() {
        return TenantContextUtils.getRequiredTenantKeyValue(tenantContextHolder);
    }

    @SneakyThrows
    public void doInTenantContext(Task task, String tenant) {
        try {
            init(tenant);
            task.doWork();
        } finally {
            destroy();
        }
    }

    @SneakyThrows
    public <R> R doInTenantContext(TaskWithResult<R> task, String tenant) {
        try {
            init(tenant);
            return task.doWork();
        } finally {
            destroy();
        }
    }


    private void init(String tenantKey) {
        TenantContextUtils.setTenant(tenantContextHolder, tenantKey);

        lepManager.beginThreadContext(threadContext -> {
            threadContext.setValue(THREAD_CONTEXT_KEY_TENANT_CONTEXT, tenantContextHolder.getContext());
            threadContext.setValue(THREAD_CONTEXT_KEY_AUTH_CONTEXT, authContextHolder.getContext());
        });
    }

    private void destroy() {
        lepManager.endThreadContext();
        tenantContextHolder.getPrivilegedContext().destroyCurrentContext();
    }

    public interface Task {
        void doWork() throws Exception;
    }

    public interface TaskWithResult<R> {
        R doWork() throws Exception;
    }
}
