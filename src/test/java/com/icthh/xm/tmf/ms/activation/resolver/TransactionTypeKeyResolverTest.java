package com.icthh.xm.tmf.ms.activation.resolver;

import com.icthh.xm.commons.lep.XmLepConstants;
import com.icthh.xm.commons.lep.spring.LepServiceHandler;
import com.icthh.xm.lep.api.LepKey;
import com.icthh.xm.lep.api.LepKeyResolver;
import com.icthh.xm.lep.api.LepManager;
import com.icthh.xm.lep.api.LepMethod;
import com.icthh.xm.lep.api.Version;
import com.icthh.xm.lep.core.CoreLepManager;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;
import com.icthh.xm.tmf.ms.activation.service.SagaService;
import com.icthh.xm.tmf.ms.activation.service.SagaServiceImpl;
import com.icthh.xm.tmf.ms.activation.service.SagaSpecService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TransactionTypeKeyResolverTest {

    private static final String TYPE_KEY = "DDS_ACTIVATION";

    @InjectMocks
    private LepServiceHandler lepServiceHandler;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private CoreLepManager lepManager;

    @Captor
    private ArgumentCaptor<LepKey> baseLepKey;

    @Mock
    private SagaSpecService sagaSpecService;

    @Mock
    private SagaService sagaService;

    @Captor
    private ArgumentCaptor<LepKeyResolver> keyResolver;

    @Captor
    private ArgumentCaptor<LepMethod> lepMethod;

    @Captor
    private ArgumentCaptor<Version> version;

    @Test
    public void testResolveLepKeyByProfileAndChannelId() throws Throwable {

        Method method = SagaServiceImpl.class.getMethod("createNewSaga", SagaTransaction.class);

        when(applicationContext.getBean(LepManager.class)).thenReturn(lepManager);

        when(sagaSpecService.getTransactionSpec(TYPE_KEY)).thenReturn(new SagaTransactionSpec());
        TransactionTypeKeyResolver resolver = new TransactionTypeKeyResolver(sagaSpecService);
        when(applicationContext.getBean(TransactionTypeKeyResolver.class)).thenReturn(resolver);

        SagaTransaction sagaTransaction = new SagaTransaction();
        sagaTransaction.setTypeKey(TYPE_KEY);

        lepServiceHandler.onMethodInvoke(SagaServiceImpl.class, sagaService, method, new Object[]{sagaTransaction});

        verify(lepManager)
            .processLep(baseLepKey.capture(), version.capture(), keyResolver.capture(), lepMethod.capture());

        LepKey resolvedKey = resolver.resolve(baseLepKey.getValue(), lepMethod.getValue(), null);

        assertEquals(
            String.join(XmLepConstants.EXTENSION_KEY_SEPARATOR,
                "service.saga.CreateNewSaga", TYPE_KEY), resolvedKey.getId());
    }
}