package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.commons.config.client.repository.TenantListRepository;
import com.icthh.xm.commons.lep.XmLepScriptConfigServerResourceLoader;
import com.icthh.xm.commons.lep.api.LepManagementService;
import com.icthh.xm.commons.security.XmAuthenticationContext;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.tmf.ms.activation.AbstractSpringBootTest;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.icthh.xm.tmf.ms.activation.domain.spec.MockSagaType.fromTypeKey;
import static java.lang.Boolean.parseBoolean;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@Slf4j
public class SagaTaskExecutorIntTest extends AbstractSpringBootTest {

    @Autowired
    private XmLepScriptConfigServerResourceLoader lepResourceLoader;

    @Autowired
    private SagaTaskExecutor sagaTaskExecutor;

    @Autowired
    private SagaSpecService sagaSpecService;

    @Autowired
    private LepManagementService lepManager;

    @Autowired
    private TenantListRepository tenantListRepository;

    @Autowired
    private TenantContextHolder tenantContextHolder;

    @Mock
    private XmAuthenticationContextHolder authContextHolder;

    @Mock
    private XmAuthenticationContext context;

    @MockBean
    private RetryService retryService;

    @Before
    public void setup() {

        TenantContextUtils.setTenant(tenantContextHolder, "XM");
        MockitoAnnotations.initMocks(this);
        when(authContextHolder.getContext()).thenReturn(context);
        when(context.getUserKey()).thenReturn(Optional.of("userKey"));

        lepManager.beginThreadContext();
        String config = loadFile("spec/activation-spec-group-test.yml");
        sagaSpecService.onRefresh("/config/tenants/XM/activation/activation-spec.yml", config);
    }

    @SneakyThrows
    public static String loadFile(String path) {
        return IOUtils.toString(new ClassPathResource(path).getInputStream(), UTF_8);
    }

    @After
    public void tearDown() {
        lepManager.endThreadContext();
        tenantContextHolder.getPrivilegedContext().destroyCurrentContext();
    }

    @Test
    public void testWithoutGroup() {
        String typeKey = "TEST-WITHOUT-GROUP";
        String path = "/";
        testTaskGroup(typeKey, path);
    }

    @Test
    public void testWithoutEmptyGroup() {
        String typeKey = "TEST-WITH-EMPTY-GROUP";
        String path = "/";
        testTaskGroup(typeKey, path);
    }

    @Test
    public void testWithGroup() {
        String typeKey = "TEST-WITH-FOLDER";
        String path = "/folder/";
        testTaskGroup(typeKey, path);
    }

    @Test
    public void testWithGroupStartFromSlash() {
        String typeKey = "TEST-WITH-FOLDER-STARTS-FROM-SLASH";
        String path = "/folder/";
        testTaskGroup(typeKey, path);
    }

    @Test
    public void testWithGroupEndSlash() {
        String typeKey = "TEST-WITH-FOLDER-END-SLASH";
        String path = "/folder/";
        testTaskGroup(typeKey, path);
    }

    @Test
    public void testWithComplexPath() {
        String typeKey = "TEST-WITH-COMPLEX-PATH";
        String path = "/folder/subfolder/subsubfolder/";
        testTaskGroup(typeKey, path);
    }

    @Test
    public void testWithComplexPathWithSlashes() {
        String typeKey = "TEST-WITH-COMPLEX-PATH-START-END-SLASH";
        String path = "/folder/subfolder/subsubfolder/";
        testTaskGroup(typeKey, path);
    }

    private void testTaskGroup(String typeKey, String path) {
        SagaTransactionSpec transactionSpec = sagaSpecService.getTransactionSpec(fromTypeKey(typeKey));
        SagaTaskSpec taskSpec = transactionSpec.getTask("TASK");
        SagaTransaction sagaTransaction = new SagaTransaction().setTypeKey(typeKey).setContext(new HashMap<>());

        var result = sagaTaskExecutor.executeTask(taskSpec, null, sagaTransaction, null);
        assertTrue("Task executed before lep created", result.isEmpty());

        sagaTaskExecutor.onFinish(sagaTransaction, Map.of());
        assertTrue("OnFinish executed before lep created", sagaTransaction.getContext().isEmpty());

        String translatedTypeKey = translateToLepConvention(typeKey);
        String basePath = "/config/tenants/XM/activation/lep/tasks";
        String taskBody = "return [isSuccess: true]";
        String onTaskPath = basePath + path + "Task$$" + translatedTypeKey + "$$TASK$$around.groovy";
        log.info("On task path {}", onTaskPath);
        lepResourceLoader.onRefresh(onTaskPath, taskBody);
        var taskResult = sagaTaskExecutor.executeTask(taskSpec, null, sagaTransaction, null);
        boolean isSuccess = parseBoolean(String.valueOf(taskResult.get("isSuccess")));
        assertTrue("Task not executed after lep created", isSuccess);

        String onFinishBody = "lepContext.inArgs.sagaTransaction.context.isSuccess = true \n"
            + "lepContext.inArgs.sagaTransaction.context.taskResult = lepContext.inArgs.taskContext.taskResult";
        String onFinishPath = basePath + path + "OnFinish$$" + translatedTypeKey + "$$around.groovy";
        log.info("On finish path {}", onFinishPath);
        lepResourceLoader.onRefresh(onFinishPath, onFinishBody);
        sagaTaskExecutor.onFinish(sagaTransaction, Map.of("taskResult", "success"));
        boolean isSuccessOnFinish = parseBoolean(String.valueOf(sagaTransaction.getContext().get("isSuccess")));
        assertTrue("OnFinish not executed after lep created", isSuccessOnFinish);
        String taskResultOnFinish = String.valueOf(sagaTransaction.getContext().get("taskResult"));
        assertEquals("OnFinish not executed after lep created", "success", taskResultOnFinish);
    }

    private static String translateToLepConvention(String xmEntitySpecKey) {
        Objects.requireNonNull(xmEntitySpecKey, "xmEntitySpecKey can't be null");
        return xmEntitySpecKey.replaceAll("-", "_").replaceAll("\\.", "\\$");
    }
}
