package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.commons.lep.LogicExtensionPoint;
import com.icthh.xm.commons.lep.XmLepScriptConfigServerResourceLoader;
import com.icthh.xm.commons.lep.api.LepManagementService;
import com.icthh.xm.commons.lep.spring.LepService;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.tmf.ms.activation.AbstractSpringBootTest;
import com.icthh.xm.tmf.ms.activation.config.LepContext;
import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Slf4j
public class LepContextCastIntTest extends AbstractSpringBootTest {

    @Autowired
    private TenantContextHolder tenantContextHolder;

    @Autowired
    private XmAuthenticationContextHolder authContextHolder;

    @Autowired
    private LepManagementService lepManager;

    @Autowired
    private XmLepScriptConfigServerResourceLoader leps;

    @Autowired
    private TestLepService testLepService;

    @MockBean
    private SagaEventRepository eventRepository;
    @MockBean
    private EventsSender eventsSender;
    @MockBean
    private SagaTransactionRepository transactionRepository;

    @SneakyThrows
    @Before
    public void setup() {
        TenantContextUtils.setTenant(tenantContextHolder, "TEST_TENANT");

        lepManager.beginThreadContext();
    }

    @BeforeTransaction
    public void beforeTransaction() {
        TenantContextUtils.setTenant(tenantContextHolder, "TEST_TENANT");
    }


    @After
    public void tearDown() {
        lepManager.endThreadContext();
        tenantContextHolder.getPrivilegedContext().destroyCurrentContext();
    }

    @Test
    @Transactional
    @SneakyThrows
    public void testLepContextCast() {
        String prefix = "/config/tenants/TEST_TENANT/activation/lep/test/";
        String key = prefix + "ScriptWithAround$$around.groovy";
        String body = "import com.icthh.xm.tmf.ms.activation.config.LepContext;\nLepContext context = lepContext\nreturn ['context':context]";
        leps.onRefresh(key, body);
        Map<String, Object> result = testLepService.sayHello();
        assertTrue(result.get("context") instanceof LepContext);
        leps.onRefresh(key, null);
    }

    @Test
    @Transactional
    @SneakyThrows
    public void testLepContextCastToMap() {
        String prefix = "/config/tenants/TEST_TENANT/activation/lep/test/";
        String key = prefix + "ScriptWithAround$$around.groovy";
        String body = "Map<String, Object> context = lepContext\nreturn ['context':context]";
        leps.onRefresh(key, body);
        Map<String, Object> result = testLepService.sayHello();
        Object context = result.get("context");
        assertEquals("GroovyMapLepContextWrapper", context.getClass().getSimpleName());
        assertTrue(context instanceof Map);
        assertTrue(context instanceof LepContext);
        leps.onRefresh(key, null);
    }

    @Configuration
    public static class TestLepConfiguration {
        @Bean
        public TestLepService testLepService() {
            return new TestLepService();
        }
    }

    @LepService(group = "test")
    public static class TestLepService {
        @LogicExtensionPoint("ScriptWithAround")
        public Map<String, Object> sayHello() {
            return Map.of();
        }
    }


}
