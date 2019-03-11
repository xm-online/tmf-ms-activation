package com.icthh.xm.tmf.ms.activation.utils;

import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_AUTH_CONTEXT;
import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_TENANT_CONTEXT;
import static com.icthh.xm.commons.tenant.TenantContextUtils.buildTenant;

import com.icthh.xm.commons.exceptions.BusinessException;
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

    public String getTenantKey() {
        return TenantContextUtils.getRequiredTenantKeyValue(tenantContextHolder);
    }

    public void doInTenantContext(Task task, String tenant) {
        tenantContextHolder.getPrivilegedContext().execute(buildTenant(tenant), () -> doWork(task));
    }

    public <R> R doInTenantContext(TaskWithResult<R> task, String tenant) {
        return tenantContextHolder.getPrivilegedContext().execute(buildTenant(tenant), () -> doWork(task));
    }

    @SneakyThrows
    private void doWork(Task task) {
        task.doWork();
    }

    @SneakyThrows
    private <R> R doWork(TaskWithResult<R>  task) {
        return task.doWork();
    }

    public interface Task {
        void doWork() throws Exception;
    }

    public interface TaskWithResult<R> {
        R doWork() throws Exception;
    }
}
