package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.commons.lep.XmLepScriptConfigServerResourceLoader;
import com.icthh.xm.commons.lep.api.LepManagementService;
import com.icthh.xm.commons.security.XmAuthenticationContext;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.tmf.ms.activation.ActivationApp;
import com.icthh.xm.tmf.ms.activation.config.SecurityBeanOverrideConfiguration;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaLogType;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;
import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import com.icthh.xm.tmf.ms.activation.events.bindings.EventHandler;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaLogRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.hibernate.envers.internal.tools.MutableInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.stream.test.binder.MessageCollectorAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.IN_QUEUE;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.WAIT_DEPENDS_TASK;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_END;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_START;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.REJECTED_BY_CONDITION;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.FINISHED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.NEW;
import static com.icthh.xm.tmf.ms.activation.service.SagaServiceImpl.LOOP_RESULT_CONTEXTS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {SagaIntTest.SagaIntTestConfiguration.class, ActivationApp.class, SecurityBeanOverrideConfiguration.class})
public class SagaIntTest {

    @Autowired
    private SagaService sagaService;

    @SpyBean
    private SagaLogRepository logRepository;

    @Autowired
    private LepManagementService lepManager;

    @Autowired
    private SagaTransactionRepository transactionRepository;

    @Autowired
    private TestEventSender testEventSender;

    @Autowired
    private TenantContextHolder tenantContextHolder;

    @Autowired
    private SagaSpecService specService;

    @Autowired
    private XmLepScriptConfigServerResourceLoader resourceLoader;

    @MockBean
    private MessageCollectorAutoConfiguration messageCollectorAutoConfiguration;

    @Before
    public void setup() {
        initContext(tenantContextHolder, lepManager);
        this.testEventSender.immediatelyProcessing = false;
        reset(logRepository, testEventSender);
    }

    private static void initContext(TenantContextHolder tenantContextHolder, LepManagementService lepManager) {
        TenantContextUtils.setTenant(tenantContextHolder, "TEST_TENANT");

        XmAuthenticationContextHolder authContextHolder = mock(XmAuthenticationContextHolder.class);
        XmAuthenticationContext context = mock(XmAuthenticationContext.class);

        when(authContextHolder.getContext()).thenReturn(context);
        when(context.getUserKey()).thenReturn(Optional.of("userKey"));
        lepManager.beginThreadContext();
    }

    @SneakyThrows
    public static String loadFile(String path) {
        return IOUtils.toString(new ClassPathResource(path).getInputStream(), UTF_8);
    }

    @After
    public void tearDown() {
        BEFORE_EVENTS.clear();
        AFTER_EVENTS.clear();
        lepManager.endThreadContext();
        tenantContextHolder.getPrivilegedContext().destroyCurrentContext();
    }

    @Test
    public void testRejectWhenTaskInManyNexts() {
        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-spec.yml", loadFile("spec/activation-spec-reject-when-task-in-many-nexts.yml"));
        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/tasks/Task$$TEST_FINISH_WHEN_REJECTED_TASK_IN_MANY_NEXTS$$FIRST$$around.groovy",
            "lepContext.inArgs.task.next=['SECOND']; return [:]");
        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/tasks/Task$$TEST_FINISH_WHEN_REJECTED_TASK_IN_MANY_NEXTS$$SECOND$$around.groovy",
            "lepContext.inArgs.task.next=['THIRD']; return [:]");

        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("TEST-FINISH-WHEN-REJECTED-TASK-IN-MANY-NEXTS")
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );

        afterEvent("FIRST").accept(sagaEvent -> {
            assertNull(getLogByTypeKey(saga, "TO_REJECT"));
        });
        afterEvent("SECOND").accept(sagaEvent -> {
            SagaLog toReject = getLogByTypeKey(saga, "TO_REJECT");
            assertNotNull(toReject);
            assertEquals(REJECTED_BY_CONDITION, toReject.getLogType());
        });
        afterEvent("THIRD").accept(sagaEvent -> {
            assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
        });

        testEventSender.startSagaProcessing();
    }

    @Test
    public void testDependsRejectStrategy() {
        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-spec.yml", loadFile("spec/activation-spec-test-depends.yml"));

        // ALL executed
        String path = "/config/tenants/TEST_TENANT/activation/lep/service/saga";
        resourceLoader.onRefresh(path + "/Condition$$TEST_DEPENDS_REJECT_STRATEGY$$B2$$around.groovy", "return true;");
        resourceLoader.onRefresh(path + "/Condition$$TEST_DEPENDS_REJECT_STRATEGY$$A1$$around.groovy", "return true;");
        runSaga("TEST-DEPENDS-REJECT-STRATEGY", EVENT_END);
        resourceLoader.onRefresh(path + "/Condition$$TEST_DEPENDS_REJECT_STRATEGY$$B2$$around.groovy", "return false;");
        runSaga("TEST-DEPENDS-REJECT-STRATEGY", REJECTED_BY_CONDITION);
        resourceLoader.onRefresh(path + "/Condition$$TEST_DEPENDS_REJECT_STRATEGY$$A1$$around.groovy", "return false;");
        runSaga("TEST-DEPENDS-REJECT-STRATEGY", REJECTED_BY_CONDITION);

        // ANY executed
        resourceLoader.onRefresh(path + "/Condition$$TEST_DEPENDS_REJECT_STRATEGY_AT_LEAST_ONE$$B2$$around.groovy", "return true;");
        resourceLoader.onRefresh(path + "/Condition$$TEST_DEPENDS_REJECT_STRATEGY_AT_LEAST_ONE$$A1$$around.groovy", "return true;");
        runSaga("TEST-DEPENDS-REJECT-STRATEGY-AT-LEAST-ONE", EVENT_END);
        resourceLoader.onRefresh(path + "/Condition$$TEST_DEPENDS_REJECT_STRATEGY_AT_LEAST_ONE$$B2$$around.groovy", "return false;");
        runSaga("TEST-DEPENDS-REJECT-STRATEGY-AT-LEAST-ONE", EVENT_END);
        resourceLoader.onRefresh(path + "/Condition$$TEST_DEPENDS_REJECT_STRATEGY_AT_LEAST_ONE$$A1$$around.groovy", "return false;");
        runSaga("TEST-DEPENDS-REJECT-STRATEGY-AT-LEAST-ONE", REJECTED_BY_CONDITION);

        // ALL_EXECUTED_OR_REJECTED executed
        resourceLoader.onRefresh(path + "/Condition$$TEST_DEPENDS_REJECT_STRATEGY_ALL_EXECUTED_OR_REJECTED$$B2$$around.groovy", "return true;");
        resourceLoader.onRefresh(path + "/Condition$$TEST_DEPENDS_REJECT_STRATEGY_ALL_EXECUTED_OR_REJECTED$$A1$$around.groovy", "return true;");
        runSaga("TEST-DEPENDS-REJECT-STRATEGY-ALL-EXECUTED-OR-REJECTED", EVENT_END);
        resourceLoader.onRefresh(path + "/Condition$$TEST_DEPENDS_REJECT_STRATEGY_ALL_EXECUTED_OR_REJECTED$$B2$$around.groovy", "return false;");
        runSaga("TEST-DEPENDS-REJECT-STRATEGY-ALL-EXECUTED-OR-REJECTED", EVENT_END);
        resourceLoader.onRefresh(path + "/Condition$$TEST_DEPENDS_REJECT_STRATEGY_ALL_EXECUTED_OR_REJECTED$$A1$$around.groovy", "return false;");
        runSaga("TEST-DEPENDS-REJECT-STRATEGY-ALL-EXECUTED-OR-REJECTED", EVENT_END);
    }

    private void runSaga(String typeKey, SagaLogType sagaLogType) {
        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey(typeKey)
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );


        MutableInteger mutableInteger = new MutableInteger();
        afterEvent("TARGET_TASK").accept(sagaEvent -> {
            mutableInteger.increase();
            log.info("Target task try: {}", mutableInteger.get());
            if (mutableInteger.get() < 2) {
                assertEquals(WAIT_DEPENDS_TASK, getEventByTypeKey(saga, "TARGET_TASK").getStatus());
                assertEquals(NEW, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
            } else {
                assertNull(getEventByTypeKey(saga, "TARGET_TASK"));
                List<SagaLog> targetTask = getLogsByTypeKey(saga, "TARGET_TASK");
                log.info("Target task logs: {}", targetTask);
                assertTrue(targetTask.stream().anyMatch(it -> it.getLogType().equals(sagaLogType)));
            }
        });

        testEventSender.startSagaProcessing();
        assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());

        BEFORE_EVENTS.clear();
        AFTER_EVENTS.clear();
    }

    @Test
    public void testDependsOnRetryStrategy() {
        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-spec.yml", loadFile("spec/activation-spec-test-depends.yml"));

        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("TEST-DEPENDS-RETRY-STRATEGY")
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );

        afterEvent("B2_SUSPENDABLE").accept(sagaEvent -> {
            assertEquals(IN_QUEUE, getEventByTypeKey(saga, "TARGET_TASK").getStatus());
        });
        afterEvent("A1").accept(sagaEvent -> {
            assertEquals(IN_QUEUE, getEventByTypeKey(saga, "TARGET_TASK").getStatus());
        });
        MutableInteger mutableInteger = new MutableInteger();
        beforeEvent("TARGET_TASK").accept(sagaEvent -> {
            mutableInteger.increase();
            if (mutableInteger.get() < 3) {
                assertEquals(IN_QUEUE, getEventByTypeKey(saga, "TARGET_TASK").getStatus());
            } else if (mutableInteger.get() >= 3 && mutableInteger.get() < 10) {
                assertEquals(IN_QUEUE, getEventByTypeKey(saga, "TARGET_TASK").getStatus());
                testEventSender.immediatelyProcessing = true; // to test case when event consumed before transaction commited
            } else if (mutableInteger.get() >= 5 && mutableInteger.get() < 10) {
                assertEquals(IN_QUEUE, getEventByTypeKey(saga, "TARGET_TASK").getStatus());
                testEventSender.immediatelyProcessing = false;
            } else if (mutableInteger.get() == 10) {
                assertEquals(IN_QUEUE, getEventByTypeKey(saga, "TARGET_TASK").getStatus());
                sagaService.continueTask(getEventByTypeKey(saga, "B2_SUSPENDABLE").getId(), Map.of());
            } else {
                assertNull(getEventByTypeKey(saga, "TARGET_TASK"));
                assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
                assertEquals(10, sagaEvent.getRetryNumber());
            }
        });

        testEventSender.startSagaProcessing();
        assertEquals(10, mutableInteger.get());
    }

    @Test
    public void testDependCheckEventuallyStrategyWithNoDepends() {
        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-spec.yml", loadFile("spec/activation-spec-test-depends.yml"));

        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("TEST-DEPENDS-STRATEGY-WITH-NO-DEPENDS")
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );

        testEventSender.startSagaProcessing();
        assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
    }

    @Test
    public void shouldSendEventForNextTaskOnlyAfterEndingPreviousOnes() {
        String sagaKey = "TEST-TASKS-ORDER";

        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-spec.yml",
            loadFile("spec/activation-spec.yml"));
        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/tasks/Task$$" + sagaKey
            + "$$FIRST-CALL.groovy", "return [items: ['A']]");

        List<String> actualOrder = new ArrayList<>();
        //@formatter:off
        List<String> expectedOrder = List.of(
            "EVENT:FIRST-CALL",
            "LOG:FIRST-CALL",
                "EVENT:SECOND-CALL",
                    "EVENT:SECOND-CALL(Iteration #0)",
                    "LOG:SECOND-CALL(Iteration #0)",
                "LOG:SECOND-CALL",
                    "EVENT:THIRD-CALL",
                    "LOG:THIRD-CALL"
        );
        //@formatter:on

        // Track EVENT_END log
        doAnswer(invocation -> {
            SagaLog log = invocation.getArgument(0, SagaLog.class);
            actualOrder.add(buildLogKey(log));

            if ("FIRST-CALL".equals(log.getEventTypeKey())) {
                Thread.sleep(2_000);
            }

            return invocation.callRealMethod();
        }).when(logRepository).save(argThat(log -> EVENT_END == log.getLogType()));

        // Track each event
        doAnswer(invocation -> {
            SagaEvent event = invocation.getArgument(0, SagaEvent.class);
            actualOrder.add(buildEventKey(event));

            return invocation.callRealMethod();
        }).when(testEventSender).sendEvent(any(SagaEvent.class));

        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey(sagaKey)
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );

        testEventSender.startSagaProcessing();
        assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
        assertEquals("The next event should only be sent after saving the EVENT_END for the current task",
            expectedOrder, actualOrder);
    }

    private String buildEventKey(SagaEvent sagaEvent) {
        String key = "EVENT:" + sagaEvent.getTypeKey();

        if (sagaEvent.getIteration() != null) {
            key += String.format("(Iteration #%d)", sagaEvent.getIteration());
        }

        return key;
    }

    private String buildLogKey(SagaLog sagaLog) {
        String key = "LOG:" + sagaLog.getEventTypeKey();

        if (sagaLog.getIteration() != null) {
            key += String.format("(Iteration #%d)", sagaLog.getIteration());
        }

        return key;
    }

    @Test
    public void testDependCheckEventuallyStrategy() {
        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-spec.yml", loadFile("spec/activation-spec-test-depends.yml"));

        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("TEST-DEPENDS-EVENTUALLY-STRATEGY")
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );

        MutableInteger mutableInteger = new MutableInteger(1);
        afterEvent("A1").accept(sagaEvent -> {
            assertTrue(mutableInteger.get() == 2);
            assertEquals(WAIT_DEPENDS_TASK, getEventByTypeKey(saga, "TARGET_TASK").getStatus());
            assertEquals(NEW, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
            assertEquals(0, sagaEvent.getRetryNumber());
            // after finish first task from "depends"
            mutableInteger.increase();
        });

        afterEvent("B2_SUSPENDABLE").accept(sagaEvent -> {
            assertTrue(mutableInteger.get() == 3);
            assertEquals(WAIT_DEPENDS_TASK, getEventByTypeKey(saga, "TARGET_TASK").getStatus());
            assertEquals(NEW, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
            assertEquals(0, sagaEvent.getRetryNumber());
            assertTrue(testEventSender.sagaEvents.isEmpty());
            sagaService.continueTask(getEventByTypeKey(saga, "B2_SUSPENDABLE").getId(), Map.of());
            // after finish first task from "depends"
            mutableInteger.increase();
        });

        afterEvent("TARGET_TASK").accept(sagaEvent -> {
            log.info("Target task try: {}", mutableInteger.get());
            assertTrue(mutableInteger.get() == 1 || mutableInteger.get() == 4);
            if (mutableInteger.get() > 1) {
                assertNull(getEventByTypeKey(saga, "TARGET_TASK"));
                assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
                assertEquals(0, sagaEvent.getRetryNumber());
            }
            mutableInteger.increase();
        });

        testEventSender.startSagaProcessing();
        assertEquals(5, mutableInteger.get());
        assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
    }

    @Test
    public void testMultiFileSagaSpec() {
        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-spec.yml", loadFile("spec/activation-spec-test-reject.yml"));
        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/service/saga/Condition$$TEST_REJECT$$B2$$around.groovy", "return false;");

        Set<String> txKeys = specService.getActualSagaSpec().getTransactions().stream().map(SagaTransactionSpec::getKey).collect(toSet());
        assertEquals(Set.of("TEST-REJECT"), txKeys);

        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-specs/version.yml", loadFile("spec/activation-spec-version.yml"));

        txKeys = specService.getActualSagaSpec().getTransactions().stream().map(SagaTransactionSpec::getKey).collect(toSet());
        assertEquals(Set.of("TEST-REJECT", "TEST-VERSION"), txKeys);

        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-specs/anotherFile.yml", loadFile("spec/activation-spec.yml"));

        txKeys = specService.getActualSagaSpec().getTransactions().stream().map(SagaTransactionSpec::getKey).collect(toSet());
        assertEquals(new HashSet<>(Set.of(
            "TEST-REJECT", "TEST-VERSION", "TASK-WITH-REJECTED-BY-CONDITION-TASK-AND-DELETED-EVENT",
            "TASK-WITH-REJECTED-AND-NON-REJECTED", "TASK-WITH-REJECTED-BY-CONDITION-TASKS",
            "TASK-AND-TASK-WITH-SUSPEND-TX", "TEST-SAGA-TYPE-KEY", "SIMPLE", "TEST-TASKS-ORDER"
        )), txKeys);

        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("TEST-REJECT")
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );
        testEventSender.startSagaProcessing();
        assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());

        SagaTransaction saga2 = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("TEST-VERSION")
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );
        testEventSender.startSagaProcessing();
        assertEquals(FINISHED, sagaService.getByKey(saga2.getKey()).getSagaTransactionState());

        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-specs/anotherFile.yml", null);
        txKeys = specService.getActualSagaSpec().getTransactions().stream().map(SagaTransactionSpec::getKey).collect(toSet());
        assertEquals(Set.of("TEST-REJECT", "TEST-VERSION"), txKeys);

        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-specs/version.yml", null);
        txKeys = specService.getActualSagaSpec().getTransactions().stream().map(SagaTransactionSpec::getKey).collect(toSet());
        assertEquals(Set.of("TEST-REJECT"), txKeys);
    }

    @Test
    public void testRejectByLepCondition() {
        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-spec.yml", loadFile("spec/activation-spec-test-reject.yml"));
        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/service/saga/Condition$$TEST_REJECT$$B2$$around.groovy", "return false;");

        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("TEST-REJECT")
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );

        testEventSender.startSagaProcessing();

        sagaService.getLogsByTransaction(saga.getId()).forEach(log -> {
            if (log.getLogType() == EVENT_START) {
                return;
            } else if (Set.of("B2", "TARGET_TASK", "TARGET_CHILD", "B21", "B22", "B221").contains(log.getEventTypeKey())) {
                assertEquals(log.getEventTypeKey() + " in invalid state", REJECTED_BY_CONDITION, log.getLogType());
            } else {
                assertEquals(log.getEventTypeKey() + " in invalid state", EVENT_END, log.getLogType());
            }
        });

        assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
    }

    @Test
    public void testContinueResolverByLepCondition() {
        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-spec.yml", loadFile("spec/activation-spec-test-continue-resolver.yml"));
        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/service/saga/ContinueTask$$TEST_CONTINUE_RESOLVER$$FIRST_SUSPENDABLE$$around.groovy",
            "lepContext.inArgs.sagaEvent.taskContext.trigger.set(true); return lepContext.lep.proceed(lepContext.lep.getMethodArgValues());");

        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("TEST-CONTINUE-RESOLVER")
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );

        AtomicBoolean trigger = new AtomicBoolean(false);
        afterEvent("FIRST_SUSPENDABLE").accept(sagaEvent -> {
            sagaService.continueTask(sagaEvent.getId(), Map.of("trigger", trigger));
        });
        beforeEvent("SECOND_SUSPENDABLE").accept(sagaEvent -> {
            assertTrue(trigger.get());
        });
        testEventSender.startSagaProcessing();
    }

    @Test
    public void testIterableLoopTask() {
        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-spec.yml", loadFile("spec/activation-spec-loops.yml"));
        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/tasks/Task$$SIMPLE-LOOP$$A.groovy",
            "return [data: [items: ['a', 'b', 'c']]]");

        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/tasks/Task$$SIMPLE-LOOP$$B.groovy",
            "return [" +
                "index: lepContext.inArgs.sagaEvent.iteration, " +
                "value: lepContext.inArgs.sagaEvent.taskContext.data.items[lepContext.inArgs.sagaEvent.iteration]" +
                "]");

        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("SIMPLE-LOOP")
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );

        testEventSender.startSagaProcessing();

        List<SagaLog> logs = sagaService.getLogsByTransaction(saga.getId());
        assertEquals(12, logs.size());
        assertTxResult(saga);
    }

    @Test
    public void testIterableWhileLoopTask() {
        String basePath = "/config/tenants/TEST_TENANT/activation";
        specService.onRefresh(basePath + "/activation-spec.yml", loadFile("spec/activation-spec-loops.yml"));
        resourceLoader.onRefresh(basePath + "/lep/tasks/Task$$SIMPLE-WHILE-LOOP$$A.groovy", "return [:]");

        resourceLoader.onRefresh(basePath + "/lep/tasks/Task$$SIMPLE-WHILE-LOOP$$B.groovy",
            "return [" +
                "index: lepContext.inArgs.sagaEvent.iteration" +
                "]");

        resourceLoader.onRefresh(basePath + "/lep/tasks/ContinueIterableLoopCondition$$SIMPLE-WHILE-LOOP$$B.groovy",
            "return lepContext.inArgs.sagaEvent.iteration < 10");

        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("SIMPLE-WHILE-LOOP")
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );

        testEventSender.startSagaProcessing();

        List<SagaLog> logs = sagaService.getLogsByTransaction(saga.getId());
        assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
        int iterableIterationsLogs = 10 * 2;
        int abcTasksLogs = 3 * 2;
        assertEquals(iterableIterationsLogs + abcTasksLogs, logs.size());
    }

    @Test
    public void testLoopWithChildTransaction() {
        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-spec.yml", loadFile("spec/activation-spec-loops.yml"));
        addLep("LOOP_WITH_CHILD_TRANSACTION", "GENERATE",
            "return [items: ['a', 'c', 'k']]"
        );
        addLep("LOOP_WITH_CHILD_TRANSACTION", "LOOP",
            "return [item: lepContext.inArgs.sagaEvent.taskContext.items[lepContext.inArgs.sagaEvent.iteration]]"
        );
        addLep("CHILD_TRANSACTION", "CONVERT_TO_CODE",
            "return [code: ((lepContext.inArgs.sagaTransaction.context.item as char) as int)]"
        );
        addLep("CHILD_TRANSACTION", "SQRT",
            "return [sqrt: lepContext.inArgs.sagaEvent.taskContext.code * lepContext.inArgs.sagaEvent.taskContext.code]"
        );
        addLep("LOOP_WITH_CHILD_TRANSACTION", "SUM",
            "return [result: lepContext.inArgs.sagaEvent.taskContext.contexts.sum { it.sqrt }]"
        );

        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("LOOP_WITH_CHILD_TRANSACTION")
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );

        testEventSender.startSagaProcessing();

        List<SagaLog> logs = sagaService.getLogsByTransaction(saga.getId());
        assertEquals(12, logs.size());
        assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
        transactionRepository.findByParentTxId(saga.getId()).forEach(childTx -> {
            assertEquals(FINISHED, childTx.getSagaTransactionState());
        });
        assertEquals('a' * 'a' + 'c' * 'c' + 'k' * 'k', logs.get(11).getTaskContext().get("result"));
    }

    private void addLep(String txKey, String taskKey, String body) {
        String activationFolder = "/config/tenants/TEST_TENANT/activation";
        resourceLoader.onRefresh(activationFolder + "/lep/tasks/Task$$" + txKey + "$$" + taskKey + ".groovy",
            body);
    }

    @Test
    public void testIterableDoWhileLoopTask() {
        String basePath = "/config/tenants/TEST_TENANT/activation";
        specService.onRefresh(basePath + "/activation-spec.yml", loadFile("spec/activation-spec-loops.yml"));
        resourceLoader.onRefresh(basePath + "/lep/tasks/Task$$SIMPLE-DO-WHILE-LOOP$$A.groovy", "return [:]");

        resourceLoader.onRefresh(basePath + "/lep/tasks/Task$$SIMPLE-DO-WHILE-LOOP$$B.groovy",
            "return [" +
                "index: lepContext.inArgs.sagaEvent.iteration" +
                "]");

        resourceLoader.onRefresh(basePath + "/lep/tasks/ContinueIterableLoopCondition$$SIMPLE-DO-WHILE-LOOP$$B.groovy",
            "return lepContext.inArgs.sagaEvent.iteration < 10");

        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("SIMPLE-DO-WHILE-LOOP")
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );

        testEventSender.startSagaProcessing();

        List<SagaLog> logs = sagaService.getLogsByTransaction(saga.getId());
        assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
        int iterableIterationsLogs = 10 * 2;
        int abcTasksLogs = 3 * 2;
        assertEquals(iterableIterationsLogs + abcTasksLogs, logs.size());
    }

    @Test
    public void testIterableLoopAsLastTask() {
        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-spec.yml", loadFile("spec/activation-spec-loops.yml"));
        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/tasks/Task$$SIMPLE-LOOP-FINISH$$A.groovy",
            "return [data: [items: ['a', 'b', 'c']]]");

        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/tasks/Task$$SIMPLE-LOOP-FINISH$$B.groovy",
            "return [" +
                "index: lepContext.inArgs.sagaEvent.iteration, " +
                "value: lepContext.inArgs.sagaEvent.taskContext.data.items[lepContext.inArgs.sagaEvent.iteration]" +
                "]");

        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("SIMPLE-LOOP-FINISH")
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );

        testEventSender.startSagaProcessing();

        List<SagaLog> logs = sagaService.getLogsByTransaction(saga.getId());
        assertEquals(10, logs.size());
        assertTxResult(saga);
    }

    @Test
    public void testLepTasksAndTransactionShortCut() {
        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-spec.yml", loadFile("spec/activation-spec.yml"));
        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/tasks/Task$$SIMPLE$$TASK_1.groovy",
            "return [data: [value: 'task1']]");
        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/tasks/Task$$SIMPLE$$TASK_2.groovy",
            "return [data: [value: 'task2'], items: ['a', 'b']]");
        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/tasks/Task$$SIMPLE$$TASK_3.groovy",
            "return [\n" +
                "fromTask1: lepContext.tasks.TASK_1.output.data.value,\n" +
                "fromTask2Input: lepContext.tasks.TASK_2.input.data.value,\n" +
                "fromTask2: lepContext.tasks.TASK_2.output.data.value,\n" +
                "fromTask3: lepContext.tasks.TASK_3.input.data.value,\n" +
                "fromTask3x2: lepContext.tasks.context.data.value,\n" +
                "fromTx: lepContext.transaction.data.field1,\n" +
                "fromParams: lepContext.taskParameters.field,\n" +
                "iteration: lepContext.iteration\n" +
                "]");

        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("SIMPLE")
            .setContext(Map.of("data", Map.of("field1", "value1")))
            .setSagaTransactionState(NEW)
        );

        testEventSender.startSagaProcessing();
        assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());

        SagaLog log = sagaService.getLogsByTransactionEventTypeAndLogType(saga.getId(), "TASK_3", EVENT_END);
        List<Map<String, Object>> contexts = (List<Map<String, Object>>) log.getTaskContext().get("contexts");
        Map<String, Object> taskContext = contexts.get(0);
        assertEquals("task1", taskContext.get("fromTask1"));
        assertEquals("task1", taskContext.get("fromTask2Input"));
        assertEquals("task2", taskContext.get("fromTask2"));
        assertEquals("task2", taskContext.get("fromTask3"));
        assertEquals("task2", taskContext.get("fromTask3x2"));
        assertEquals("value1", taskContext.get("fromTx"));
        assertEquals("paramField", taskContext.get("fromParams"));
        assertEquals(0, contexts.get(0).get("iteration"));
        assertEquals(1, contexts.get(1).get("iteration"));
    }

    private void assertTxResult(SagaTransaction saga) {
        assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
        for (Integer i = 0; i < 3; i++) {
            SagaLog log = logRepository.findFinishLogTypeKeyAndIteration(saga.getId(), "B", i).get();
            assertEquals(i, log.getIteration());
            assertEquals(Integer.valueOf(3), log.getIterationsCount());
            char value = (char) ('a' + i);
            assertEquals(Map.of("value", "" + value, "index", i), log.getTaskContext());
        }
        SagaLog log = sagaService.getLogsByTransactionEventTypeAndLogType(saga.getId(), "B", EVENT_END);
        assertEquals(Map.of(
            LOOP_RESULT_CONTEXTS,
            List.of(
                Map.of("index", 0, "value", "a"),
                Map.of("index", 1, "value", "b"),
                Map.of("index", 2, "value", "c")
            )
        ), log.getTaskContext());
    }

    @Test
    public void testIterableNumberTask() {
        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-spec.yml", loadFile("spec/activation-spec-loops.yml"));
        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/tasks/Task$$SIMPLE-LOOP$$A.groovy",
            "return [data: [items: 3]]");

        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/tasks/Task$$SIMPLE-LOOP$$B.groovy",
            "return [index: lepContext.inArgs.sagaEvent.iteration]");

        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("SIMPLE-LOOP")
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );

        testEventSender.startSagaProcessing();

        List<SagaLog> logs = sagaService.getLogsByTransaction(saga.getId());
        assertEquals(12, logs.size());
        assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
        for (Integer i = 0; i < 3; i++) {
            SagaLog log = logRepository.findFinishLogTypeKeyAndIteration(saga.getId(), "B", i).get();
            assertEquals(i, log.getIteration());
            assertEquals(Integer.valueOf(3), log.getIterationsCount());
            assertEquals(Map.of("index", i), log.getTaskContext());
        }
        SagaLog log = sagaService.getLogsByTransactionEventTypeAndLogType(saga.getId(), "B", EVENT_END);
        assertEquals(Map.of(
            LOOP_RESULT_CONTEXTS,
            List.of(
                Map.of("index", 0),
                Map.of("index", 1),
                Map.of("index", 2)
            )
        ), log.getTaskContext());
    }

    @Test
    public void testIterableSkipTask() {
        testIterableSkipTask("[]");
        testIterableSkipTask("0");
        testIterableSkipTask("null");
    }

    private void testIterableSkipTask(String items) {
        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-spec.yml", loadFile("spec/activation-spec-loops.yml"));
        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/tasks/Task$$SIMPLE-LOOP$$A.groovy",
            "return [data: [items: " + items + "]]");

        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("SIMPLE-LOOP")
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );

        testEventSender.startSagaProcessing();
        List<SagaLog> logs = sagaService.getLogsByTransaction(saga.getId());
        assertEquals(6, logs.size());
        assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
    }

    @Test
    public void testIterableSkipErrorTask() {
        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-spec.yml", loadFile("spec/activation-spec-loops.yml"));
        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/tasks/Task$$SIMPLE-LOOP-SKIP-ERROR$$A.groovy",
            "return [data: [items: ['a', 'b', 'c']]]");

        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("SIMPLE-LOOP-SKIP-ERROR")
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );

        testEventSender.startSagaProcessing();
        List<SagaLog> logs = sagaService.getLogsByTransaction(saga.getId());
        assertEquals(6, logs.size());
        assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
    }

    @Test
    public void testRejectTaskThatPresentInSuspendableTask() {
        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-spec.yml", loadFile("spec/activation-spec-reject-in-suspendable.yml"));
        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/tasks/Task$$TEST_SUSPENDABLE_REJECT$$SUSPENDABLE_TASK.groovy",
            "lepContext.inArgs.task.next=['TARGET_TASK']; return [:]");
        resourceLoader.onRefresh("/config/tenants/TEST_TENANT/activation/lep/tasks/Task$$TEST_SUSPENDABLE_REJECT$$REJECTED_TASK.groovy",
            "throw new RuntimeException('Error has happened')");

        SagaTransaction saga = sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("TEST_SUSPENDABLE_REJECT")
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );

        afterEvent("SUSPENDABLE_TASK").accept(sagaEvent -> {
            sagaService.continueTask(sagaEvent.getId(), Map.of());
        });

        testEventSender.startSagaProcessing();
    }

    @Test
    public void testSagaTxVersion() {
        specService.onRefresh("/config/tenants/TEST_TENANT/activation/activation-spec.yml", loadFile("spec/activation-spec-version.yml"));

        sagaService.createNewSaga(new SagaTransaction()
            .setKey(UUID.randomUUID().toString())
            .setTypeKey("TEST-VERSION")
            .setContext(Map.of())
            .setSagaTransactionState(NEW)
        );

        AtomicBoolean trigger = new AtomicBoolean(false);
        beforeEvent("EVENT").accept(sagaEvent -> {
            trigger.set(true);
            SagaTransaction sagaTransaction = sagaService.findTransactionById(sagaEvent.getTransactionId()).get();
            assertEquals("54321", sagaTransaction.getSpecificationVersion());
        });
        testEventSender.startSagaProcessing();
        assertTrue(trigger.get());
    }

    private SagaEvent getEventByTypeKey(SagaTransaction saga, String targetTask) {
        List<SagaEvent> events = sagaService.getEventsByTransaction(saga.getId());
        return events.stream().filter(e -> e.getTypeKey().equals(targetTask)).findFirst().orElse(null);
    }

    private SagaLog getLogByTypeKey(SagaTransaction saga, String targetTask) {
        List<SagaLog> events = sagaService.getLogsByTransaction(saga.getId());
        return events.stream().filter(e -> e.getEventTypeKey().equals(targetTask)).findFirst().orElse(null);
    }

    private List<SagaLog> getLogsByTypeKey(SagaTransaction saga, String targetTask) {
        List<SagaLog> events = sagaService.getLogsByTransaction(saga.getId());
        return events.stream().filter(e -> e.getEventTypeKey().equals(targetTask)).collect(toList());
    }

    public static class SagaIntTestConfiguration {

        @Primary
        @Bean
        public RetryService retryService(
            ThreadPoolTaskScheduler threadPoolTaskScheduler,
            EventsSender eventsSender,
            SagaEventRepository sagaEventRepository,
            SagaTransactionRepository transactionRepository,
            TenantUtils tenantUtils,
            SeparateTransactionExecutor separateTransactionExecutor
        ) {
            return new RetryService(
                threadPoolTaskScheduler,
                eventsSender,
                sagaEventRepository,
                transactionRepository,
                tenantUtils,
                separateTransactionExecutor
            ) {
                @Override
                public void retry(SagaEvent sagaEvent, SagaTransaction sagaTransaction, SagaTaskSpec sagaTaskSpec) {
                    throw new RuntimeException("Error has happened");
                }

                @Override
                public Map<String, Object> retryLimitExceeded(SagaEvent sagaEvent, SagaTaskSpec task, SagaEvent.SagaEventStatus eventStatus) {
                    throw new RuntimeException("Error has happened");
                }

                @Override
                protected void putToScheduler(SagaEvent savedSagaEvent) {
                    doResend(savedSagaEvent);
                }
            };
        }

        @Primary
        @Bean
        public EventsSender eventsSender(@Lazy EventHandler eventHandler, TenantContextHolder tenantContextHolder, LepManagementService lepManager) {
            return spy(new TestEventSender(eventHandler, () -> initContext(tenantContextHolder, lepManager)));
        }
    }

    private static List<Consumer<SagaEvent>> BEFORE_EVENTS = new ArrayList<>();
    private static List<Consumer<SagaEvent>> AFTER_EVENTS = new ArrayList<>();

    public static Consumer<Consumer<SagaEvent>> beforeEvent(String typeKey) {
        return onEvent(typeKey, BEFORE_EVENTS);
    }

    public static Consumer<Consumer<SagaEvent>> afterEvent(String typeKey) {
        return onEvent(typeKey, AFTER_EVENTS);
    }

    private static Consumer<Consumer<SagaEvent>> onEvent(String typeKey, List<Consumer<SagaEvent>> handlers) {
        return sagaEventConsumer -> handlers.add(sagaEvent -> {
            if (typeKey.equals(sagaEvent.getTypeKey())) {
                sagaEventConsumer.accept(sagaEvent);
            }
        });
    }

    @RequiredArgsConstructor
    public static class TestEventSender implements EventsSender {

        public volatile boolean immediatelyProcessing = false;
        public volatile boolean started = false;

        public final LinkedList<SagaEvent> sagaEvents = new LinkedList<>();

        private final EventHandler eventHandler;
        private final Runnable initContext;

        @Override
        public void sendEvent(SagaEvent sagaEvent) {
            log.info("sendEvent.typeKey: {}", sagaEvent.getTypeKey());
            sagaEvents.addLast(sagaEvent);
            if (immediatelyProcessing && started) {
                this.processNextEvent();
            }
        }

        public void startSagaProcessing() {
            this.started = true;
            while (!sagaEvents.isEmpty()) {
                processNextEvent();
            }
        }

        @SneakyThrows
        private void processNextEvent() {
            log.info("queue state: {}", sagaEvents.stream().map(SagaEvent::getTypeKey).collect(toList()));
            SagaEvent event = sagaEvents.removeFirst();
            log.info("\"get\" event from \"queue\": {}", event.getTypeKey());

            BEFORE_EVENTS.forEach(handler -> handler.accept(event));
            Thread thread = new Thread(() -> {
                try {
                    eventHandler.onEvent(event, "TEST_TENANT");
                } catch (Exception e) {
                    log.error("Error has happened", e);
                }
            });
            thread.start();
            thread.join();

            initContext.run();
            if (!immediatelyProcessing) {
                AFTER_EVENTS.forEach(handler -> handler.accept(event));
            }
        }

        @Override
        public void resendEvent(SagaEvent sagaEvent) {
            log.info("resendEvent: {}", sagaEvent);
            throw new RuntimeException("Error has happened");
        }
    }

}


