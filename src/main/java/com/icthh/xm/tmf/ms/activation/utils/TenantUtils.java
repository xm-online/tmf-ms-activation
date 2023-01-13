package com.icthh.xm.tmf.ms.activation.utils;

import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_AUTH_CONTEXT;
import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_TENANT_CONTEXT;

import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.commons.tenant.TenantKey;
import com.icthh.xm.lep.api.LepManager;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

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

    /**
     * Run code in context with specified tenant. Keep in mind, that after task is executed tenant context
     * will be destroyed.
     * NOTICE: If run before application start up: consider to run in new thread (or setting
     * previous tenant after method call), otherwise tenant context for main thread
     * (spring bean context initialization thread) will be destroyed, which will lead to
     * errors in further beans initialization that rely on current tenant context.
     *
     * @param task task that will be run in context with specified tenant.
     * @param tenant tenant name that will be set to tenant context.
     */
    @SneakyThrows
    public void doInTenantContext(Task task, String tenant) {
        try {
            init(tenant);
            task.doWork();
        } finally {
            destroy();
        }
    }

    /**
     * Run code in context with specified tenant and return result of the task.
     * Keep in mind, that after task is executed tenant context will be destroyed.
     * NOTICE: If run before application start up: consider to run in new thread (or setting
     * previous tenant after method call), otherwise tenant context for main thread
     * (spring bean context initialization thread) will be destroyed, which will lead to
     * errors in further beans initialization that rely on current tenant context.
     *
     * @param task task that will be run in context with specified tenant.
     * @param tenant tenant name that will be set to tenant context.
     * @param <R> type of the result.
     * @return result of the task execution.
     */
    @SneakyThrows
    public <R> R doInTenantContext(TaskWithResult<R> task, String tenant) {
        try {
            init(tenant);
            return task.doWork();
        } finally {
            destroy();
        }
    }

    public void setTenantKey(TenantKey tenantKey) {
        TenantContextUtils.setTenant(tenantContextHolder, tenantKey);
    }

    public Optional<TenantKey> getOptionalTenantKey() {
        return TenantContextUtils.getTenantKey(tenantContextHolder);
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
