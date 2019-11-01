package com.icthh.xm.tmf.ms.activation.resolver;

import com.icthh.xm.commons.lep.XmLepConstants;
import com.icthh.xm.commons.lep.spring.LepServiceHandler;
import com.icthh.xm.lep.api.LepKey;
import com.icthh.xm.lep.api.LepKeyResolver;
import com.icthh.xm.lep.api.LepManager;
import com.icthh.xm.lep.api.LepMethod;
import com.icthh.xm.lep.api.Version;
import com.icthh.xm.lep.core.CoreLepManager;
import com.icthh.xm.tmf.ms.activation.model.Service;
import com.icthh.xm.tmf.ms.activation.web.rest.ServiceApiImpl;
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
public class SyncKeyResolverTest {

    @InjectMocks
    private LepServiceHandler lepServiceHandler;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private CoreLepManager lepManager;

    @Captor
    private ArgumentCaptor<LepKey> baseLepKey;

    @Captor
    private ArgumentCaptor<LepKeyResolver> keyResolver;

    @Captor
    private ArgumentCaptor<LepMethod> lepMethod;

    @Captor
    private ArgumentCaptor<Version> version;

    @Test
    public void testResolveLepKeyByServiceType() throws Throwable {

        Service service = new Service();
        service.setType("DDS-ACTIVATION");

        Method method = ServiceApiImpl.class.getMethod("serviceCreate", Service.class);

        when(applicationContext.getBean(LepManager.class)).thenReturn(lepManager);

        SyncKeyResolver resolver = new SyncKeyResolver();
        when(applicationContext.getBean(SyncKeyResolver.class)).thenReturn(resolver);

        lepServiceHandler.onMethodInvoke(ServiceApiImpl.class,
            new ServiceApiImpl(null), method, new Service[]{service});

        verify(lepManager)
            .processLep(baseLepKey.capture(), version.capture(), keyResolver.capture(), lepMethod.capture());

        LepKey resolvedKey = resolver.resolve(baseLepKey.getValue(), lepMethod.getValue(), null);

        assertEquals(
            String.join(XmLepConstants.EXTENSION_KEY_SEPARATOR,
                "sync.execution", "Sync", "DDS-ACTIVATION"), resolvedKey.getId());
    }
}