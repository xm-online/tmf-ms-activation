package com.icthh.xm.tmf.ms.activation.web.rest.v4;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icthh.xm.commons.i18n.error.web.ExceptionTranslator;
import com.icthh.xm.commons.i18n.spring.service.LocalizationMessageService;
import com.icthh.xm.tmf.ms.activation.api.v4.ServiceResourceApiController;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.mapper.ServiceMapperImpl;
import com.icthh.xm.tmf.ms.activation.model.v4.Characteristic;
import com.icthh.xm.tmf.ms.activation.model.v4.RelatedParty;
import com.icthh.xm.tmf.ms.activation.model.v4.RelatedServiceOrderItem;
import com.icthh.xm.tmf.ms.activation.model.v4.ServiceCreate;
import com.icthh.xm.tmf.ms.activation.model.v4.ServiceSpecificationRef;
import com.icthh.xm.tmf.ms.activation.model.v4.ServiceStateType;
import com.icthh.xm.tmf.ms.activation.service.ResponseEnricherServiceImpl;
import com.icthh.xm.tmf.ms.activation.service.SagaService;
import com.icthh.xm.tmf.ms.activation.service.SagaTransactionFactory;
import com.icthh.xm.tmf.ms.activation.web.rest.TestUtil;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

@RunWith(SpringRunner.class)
@WebMvcTest(controllers = ServiceResourceApiController.class, secure = false)
@ContextConfiguration(classes = {ServiceResourceApiController.class, ServiceResourceApiImpl.class,
    ExceptionTranslator.class, ResponseEnricherServiceImpl.class, ServiceMapperImpl.class})
public class ServiceResourceApiImplTest {

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
        //given
        String msisdn = "380764563728";
        String iccid = "860000013242";
        ServiceStateType state = ServiceStateType.ACTIVE;
        String orderItemId = "b5690b1b-094c-44d0-87ff-6cb3bb37c4c3";
        String orderId = "9e282084-daaf-49e3-bbc6-1f6f77e13c7a";

        String msisdnKey = "msisdn";
        String stateKey = "state";
        String iccidKey = "ICCID";
        String relatedPartyId = "relatedParty.id";
        String relatedPartyReferredType = "relatedParty.referredType";
        String itemIdKey = "itemId";
        String serviceOrderIdKey = "serviceOrderId";
        String serviceOrderItemKey = "serviceOrderItem";

        String expectedTypeKey = "SOME_SERVICE";
        Map<String, Object> expectedContext = new HashMap<>();
        expectedContext.put(msisdnKey, msisdn);
        expectedContext.put(stateKey, state);
        expectedContext.put(iccidKey, iccid);
        expectedContext.put(relatedPartyId, msisdn);
        expectedContext.put(relatedPartyReferredType, msisdnKey);

        expectedContext.put(serviceOrderItemKey,
            List.of(Map.of(itemIdKey, orderItemId, serviceOrderIdKey, orderId)));

        RelatedParty relatedParty = new RelatedParty();
        relatedParty.setId(msisdn);
        relatedParty.setAtReferredType(msisdnKey);
        Characteristic characteristic = new Characteristic();
        characteristic.setName(iccidKey);
        characteristic.setValue(iccid);
        ServiceSpecificationRef serviceSpecification = new ServiceSpecificationRef();
        serviceSpecification.setId(expectedTypeKey);
        RelatedServiceOrderItem serviceOrderItem = new RelatedServiceOrderItem();
        serviceOrderItem.setItemId(orderItemId);
        serviceOrderItem.setServiceOrderId(orderId);
        ServiceCreate serviceCreate = new ServiceCreate();
        serviceCreate.setRelatedParty(Collections.singletonList(relatedParty));
        serviceCreate.setServiceCharacteristic(Collections.singletonList(characteristic));
        serviceCreate.setServiceSpecification(serviceSpecification);
        serviceCreate.setServiceOrderItem(Collections.singletonList(serviceOrderItem));
        serviceCreate.setState(state);

        String id = UUID.randomUUID().toString();
        when(sagaService.createNewSaga(ArgumentMatchers.any()))
            .thenReturn(new SagaTransaction().setId(id));

        //when
        mockMvc.perform(post("/tmf-api/ServiceActivationAndConfiguration/v4/service")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(serviceCreate)))
        //then
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", Matchers.is(id)));

        verify(sagaTransactionFactory).createSagaTransaction(expectedTypeKey, expectedContext);
    }
}
