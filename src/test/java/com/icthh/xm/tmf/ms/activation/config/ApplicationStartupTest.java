package com.icthh.xm.tmf.ms.activation.config;

import com.icthh.xm.commons.security.XmAuthenticationContext;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.lep.api.LepManager;
import com.icthh.xm.tmf.ms.activation.ActivationApp;
import com.icthh.xm.tmf.ms.activation.config.SecurityBeanOverrideConfiguration;
import com.icthh.xm.tmf.ms.activation.service.RetryService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.test.binder.MessageCollectorAutoConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Optional;

import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_AUTH_CONTEXT;
import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_TENANT_CONTEXT;
import static org.mockito.Mockito.*;


@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {ActivationApp.class, SecurityBeanOverrideConfiguration.class})
@EnableAutoConfiguration(exclude = MessageCollectorAutoConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public class ApplicationStartupTest {

    @MockBean
    private RetryService retryService;
    @Autowired
    private LepManager lepManager;
    @Autowired
    private TenantContextHolder tenantContextHolder;
    @Mock
    private XmAuthenticationContext context;
    @Mock
    private XmAuthenticationContextHolder authContextHolder;

    @After
    public void destroy() {
        lepManager.endThreadContext();
        tenantContextHolder.getPrivilegedContext().destroyCurrentContext();
    }

    @Before
    public void init() {
        TenantContextUtils.setTenant(tenantContextHolder, "TEST_TENANT");
        MockitoAnnotations.initMocks(this);
        when(authContextHolder.getContext()).thenReturn(context);
        when(context.getUserKey()).thenReturn(Optional.of("userKey"));

        lepManager.beginThreadContext(ctx -> {
            ctx.setValue(THREAD_CONTEXT_KEY_TENANT_CONTEXT, tenantContextHolder.getContext());
            ctx.setValue(THREAD_CONTEXT_KEY_AUTH_CONTEXT, authContextHolder.getContext());
        });
    }

    @Test
    @SneakyThrows
    public void shouldRescheduleAllEventsAfterServiceStartup() {
        verify(retryService, atLeastOnce()).rescheduleAllEvents();
    }
}
