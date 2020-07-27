package com.icthh.xm.tmf.ms.activation.web.rest.v4;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icthh.xm.commons.i18n.error.web.ExceptionTranslator;
import com.icthh.xm.commons.i18n.spring.service.LocalizationMessageService;
import com.icthh.xm.tmf.ms.activation.api.v4.ServiceResourceApiController;
import com.icthh.xm.tmf.ms.activation.mapper.ServiceMapper;
import com.icthh.xm.tmf.ms.activation.model.v4.Characteristic;
import com.icthh.xm.tmf.ms.activation.model.v4.RelatedParty;
import com.icthh.xm.tmf.ms.activation.model.v4.ServiceCreate;
import com.icthh.xm.tmf.ms.activation.model.v4.ServiceSpecificationRef;
import com.icthh.xm.tmf.ms.activation.model.v4.ServiceStateType;
import com.icthh.xm.tmf.ms.activation.service.SagaService;
import com.icthh.xm.tmf.ms.activation.service.SagaTransactionFactory;
import com.icthh.xm.tmf.ms.activation.web.rest.TestUtil;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

@RunWith(SpringRunner.class)
@WebMvcTest(controllers = ServiceResourceApiController.class, secure = false)
@ContextConfiguration(classes = {ServiceResourceApiController.class, ServiceResourceApiImpl.class, ExceptionTranslator.class})
public class ServiceResourceApiImplTest {

    @MockBean
    private ServiceMapper serviceMapper;

    @MockBean
    private SagaService sagaService;

    @MockBean
    private LocalizationMessageService localizationMessageService;

    @MockBean
    private SagaTransactionFactory sagaTransactionFactory;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @SneakyThrows
    public void testCreateService() {
        String msisdn = "380764563728";
        String iccid = "860000013242";

        String msisdnKey = "msisdn";
        String iccidKey = "ICCID";

        String expectedTypeKey = "SOME_SERVICE";
        Map<String, Object> expectedContext = new HashMap<>();
        expectedContext.put(msisdnKey, msisdn);
        expectedContext.put(iccidKey, iccid);

        RelatedParty relatedParty = new RelatedParty();
        relatedParty.setId(msisdn);
        relatedParty.setAtReferredType(msisdnKey);
        Characteristic characteristic = new Characteristic();
        characteristic.setName(iccidKey);
        characteristic.setValue(iccid);
        ServiceSpecificationRef serviceSpecification = new ServiceSpecificationRef();
        serviceSpecification.setId(expectedTypeKey);
        ServiceCreate serviceCreate = new ServiceCreate();
        serviceCreate.setRelatedParty(Collections.singletonList(relatedParty));
        serviceCreate.setServiceCharacteristic(Collections.singletonList(characteristic));
        serviceCreate.setServiceSpecification(serviceSpecification);
        serviceCreate.setState(ServiceStateType.ACTIVE);

        mockMvc.perform(post("/tmf-api/ServiceActivationAndConfiguration/v4/service")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(serviceCreate)))
            .andExpect(status().isCreated());

        verify(sagaTransactionFactory).createSagaTransaction(eq(expectedTypeKey), eq(expectedContext));
    }
}
