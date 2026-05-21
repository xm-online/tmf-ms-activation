package com.icthh.xm.tmf.ms.activation.config;

import com.icthh.xm.commons.lep.api.LepManagementService;
import com.icthh.xm.commons.security.XmAuthenticationContext;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.tmf.ms.activation.AbstractSpringBootTest;
import com.icthh.xm.tmf.ms.activation.service.RetryService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Optional;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public class ApplicationStartupIntTest extends AbstractSpringBootTest {

    @MockitoBean
    private RetryService retryService;
    @Autowired
    private LepManagementService lepManager;
    @Autowired
    private TenantContextHolder tenantContextHolder;
    @Mock
    private XmAuthenticationContext context;
    @Mock
    private XmAuthenticationContextHolder authContextHolder;

    @AfterEach
    public void destroy() {
        lepManager.endThreadContext();
        tenantContextHolder.getPrivilegedContext().destroyCurrentContext();
    }

    @BeforeEach
    public void init() {
        TenantContextUtils.setTenant(tenantContextHolder, "TEST_TENANT");
        MockitoAnnotations.openMocks(this);
        when(authContextHolder.getContext()).thenReturn(context);
        when(context.getUserKey()).thenReturn(Optional.of("userKey"));

        lepManager.beginThreadContext();
    }

    @Test
    @SneakyThrows
    public void shouldRescheduleAllEventsAfterServiceStartup() {
        verify(retryService, atLeastOnce()).rescheduleAllEvents();
    }
}
