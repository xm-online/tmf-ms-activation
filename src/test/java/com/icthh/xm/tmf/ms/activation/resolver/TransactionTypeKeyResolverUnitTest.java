package com.icthh.xm.tmf.ms.activation.resolver;

import com.icthh.xm.commons.lep.LogicExtensionPoint;
import com.icthh.xm.commons.lep.api.LepKey;
import com.icthh.xm.commons.lep.impl.DefaultLepKey;
import com.icthh.xm.commons.lep.impl.LepMethodImpl;
import com.icthh.xm.commons.lep.impl.MethodSignatureImpl;
import com.icthh.xm.commons.lep.spring.LepService;
import com.icthh.xm.lep.api.LepMethod;
import com.icthh.xm.lep.api.MethodSignature;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;
import com.icthh.xm.tmf.ms.activation.service.SagaServiceImpl;
import com.icthh.xm.tmf.ms.activation.service.SagaSpecService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Method;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TransactionTypeKeyResolverUnitTest {

    private static final String TYPE_KEY = "DDS_ACTIVATION";

    @Mock
    private SagaSpecService sagaSpecService;
    @Mock
    private SagaServiceImpl sagaService;

    @Test
    public void testResolveLepKeyByProfileAndChannelId() throws Throwable {
        Class<?> targetType = SagaServiceImpl.class;
        Object target = sagaService;
        Method method = SagaServiceImpl.class.getMethod("createNewSaga", SagaTransaction.class);
        LogicExtensionPoint lep = method.getAnnotation(LogicExtensionPoint.class);
        LepService typeLepService = targetType.getAnnotation(LepService.class);
        String group = isNotBlank(lep.group()) ? lep.group() : typeLepService.group();
        LepKey baseLepKey = new DefaultLepKey(group, lep.value());

        SagaTransaction sagaTransaction = new SagaTransaction();
        sagaTransaction.setTypeKey(TYPE_KEY);

        Object[] args = new Object[1];
        args[0] = sagaTransaction;

        MethodSignature methodSignature = new MethodSignatureImpl(method, targetType);
        LepMethod lepMethod = new LepMethodImpl(target, methodSignature, args, baseLepKey);

        when(sagaSpecService.getTransactionSpec(sagaTransaction)).thenReturn(new SagaTransactionSpec());
        TransactionTypeKeyResolver transactionTypeKeyResolver = new TransactionTypeKeyResolver(sagaSpecService);
        String lepGroup = transactionTypeKeyResolver.group(lepMethod);
        List<String> segments = transactionTypeKeyResolver.segments(lepMethod);

        assertEquals("service.saga", lepGroup);
        assertEquals(1, segments.size());
        assertEquals(TYPE_KEY, segments.get(0));
    }
}