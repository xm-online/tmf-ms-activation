package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.commons.lep.XmLepScriptConfigServerResourceLoader;
import com.icthh.xm.commons.security.XmAuthenticationContext;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.lep.api.LepManager;
import com.icthh.xm.tmf.ms.activation.ActivationApp;
import com.icthh.xm.tmf.ms.activation.config.SecurityBeanOverrideConfiguration;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import com.icthh.xm.tmf.ms.activation.events.bindings.EventHandler;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
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
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.test.binder.MessageCollectorAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_AUTH_CONTEXT;
import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_TENANT_CONTEXT;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.IN_QUEUE;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.WAIT_DEPENDS_TASK;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_END;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_START;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.REJECTED_BY_CONDITION;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.FINISHED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.NEW;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {SagaIntTest.SagaIntTestConfiguration.class, ActivationApp.class, SecurityBeanOverrideConfiguration.class})
@EnableAutoConfiguration(exclude = MessageCollectorAutoConfiguration.class)
public class SagaIntTest {

    @Autowired
    private SagaService sagaService;

    @Autowired
    private LepManager lepManager;

    @Autowired
    private TestEventSender testEventSender;

    @Autowired
    private TenantContextHolder tenantContextHolder;

    @Autowired
    private SagaSpecService specService;

    @Autowired
    private XmLepScriptConfigServerResourceLoader resourceLoader;

    @Before
    public void setup() {
        initContext(tenantContextHolder, lepManager);
    }

    private static void initContext(TenantContextHolder tenantContextHolder, LepManager lepManager) {
        TenantContextUtils.setTenant(tenantContextHolder, "TEST_TENANT");

        XmAuthenticationContextHolder authContextHolder = mock(XmAuthenticationContextHolder.class);
        XmAuthenticationContext context = mock(XmAuthenticationContext.class);

        when(authContextHolder.getContext()).thenReturn(context);
        when(context.getUserKey()).thenReturn(Optional.of("userKey"));
        lepManager.beginThreadContext(ctx -> {
            ctx.setValue(THREAD_CONTEXT_KEY_TENANT_CONTEXT, tenantContextHolder.getContext());
            ctx.setValue(THREAD_CONTEXT_KEY_AUTH_CONTEXT, authContextHolder.getContext());
        });
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
        afterEvent("TARGET_TASK").accept(sagaEvent -> {
            mutableInteger.increase();
            if (mutableInteger.get() < 5) {
                assertEquals(IN_QUEUE, getEventByTypeKey(saga, "TARGET_TASK").getStatus());
            } else if (mutableInteger.get() == 5) {
                assertEquals(IN_QUEUE, getEventByTypeKey(saga, "TARGET_TASK").getStatus());
                sagaService.continueTask(getEventByTypeKey(saga, "B2_SUSPENDABLE").getId(), Map.of());
            } else {
                assertNull(getEventByTypeKey(saga, "TARGET_TASK"));
                assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
                assertEquals(5, sagaEvent.getRetryNumber());
            }
        });

        testEventSender.startSagaProcessing();
        assertEquals(6, mutableInteger.get());
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
        afterEvent("TARGET_TASK").accept(sagaEvent -> {
            log.info("Target task try: {}", mutableInteger.get());

            // after start
            if (mutableInteger.get() == 1) {
                assertEquals(WAIT_DEPENDS_TASK, getEventByTypeKey(saga, "TARGET_TASK").getStatus());
                assertEquals(NEW, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
                assertEquals(0, sagaEvent.getRetryNumber());
                // after finish first task from "depends"
            } else if (mutableInteger.get() == 2) {
                assertEquals(WAIT_DEPENDS_TASK, getEventByTypeKey(saga, "TARGET_TASK").getStatus());
                assertEquals(NEW, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
                assertEquals(0, sagaEvent.getRetryNumber());
                assertTrue(testEventSender.sagaEvents.isEmpty());
                sagaService.continueTask(getEventByTypeKey(saga, "B2_SUSPENDABLE").getId(), Map.of());
                // after finish second task from "depends"
            } else if (mutableInteger.get() == 3) {
                assertNull(getEventByTypeKey(saga, "TARGET_TASK"));
                assertEquals(FINISHED, sagaService.getByKey(saga.getKey()).getSagaTransactionState());
                assertEquals(0, sagaEvent.getRetryNumber());
            }

            mutableInteger.increase();
        });

        testEventSender.startSagaProcessing();
        assertEquals(4, mutableInteger.get());
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
                public void retryForWaitDependsTask(SagaEvent sagaEvent, SagaTransaction sagaTransaction, SagaTaskSpec sagaTaskSpec) {
                    sagaEvent.setRetryNumber(sagaEvent.getRetryNumber() + 1);
                    eventsSender.sendEvent(sagaEvent);
                }
            };
        }

        @Primary
        @Bean
        public EventsSender eventsSender(@Lazy EventHandler eventHandler, TenantContextHolder tenantContextHolder, LepManager lepManager) {
            return new TestEventSender(eventHandler, () -> initContext(tenantContextHolder, lepManager));
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

        public final LinkedList<SagaEvent> sagaEvents = new LinkedList<>();

        private final EventHandler eventHandler;
        private final Runnable initContext;

        @Override
        public void sendEvent(SagaEvent sagaEvent) {
            log.info("sendEvent.typeKey: {}", sagaEvent.getTypeKey());
            sagaEvents.addLast(sagaEvent);
        }

        public void startSagaProcessing() {
            while (!sagaEvents.isEmpty()) {
                processNextEvent();
            }
        }

        private void processNextEvent() {
            log.info("queue state: {}", sagaEvents.stream().map(SagaEvent::getTypeKey).collect(toList()));
            SagaEvent event = sagaEvents.getFirst();
            log.info("\"get\" event from \"queue\": {}", event.getTypeKey());

            BEFORE_EVENTS.forEach(handler -> handler.accept(event));
            eventHandler.onEvent(event, "TEST_TENANT");
            sagaEvents.removeFirst();

            initContext.run();
            AFTER_EVENTS.forEach(handler -> handler.accept(event));
        }

        @Override
        public void resendEvent(SagaEvent sagaEvent) {
            log.info("resendEvent: {}", sagaEvent);
            throw new RuntimeException("Error has happened");
        }
    }

}


