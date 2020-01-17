package com.icthh.xm.tmf.ms.activation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icthh.xm.commons.i18n.error.web.ExceptionTranslator;
import com.icthh.xm.commons.i18n.spring.service.LocalizationMessageService;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.model.RelatedParty;
import com.icthh.xm.tmf.ms.activation.model.Service;
import com.icthh.xm.tmf.ms.activation.service.SagaService;
import com.icthh.xm.tmf.ms.activation.web.rest.ServiceApiImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

import static com.icthh.xm.tmf.ms.activation.web.rest.ServiceApiImpl.REQUEST_ATTRIBUTES_KEY;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Slf4j
@RunWith(SpringRunner.class)
@WebMvcTest(controllers = ServiceApiController.class)
@ContextConfiguration(classes = {ServiceApiController.class, ExceptionTranslator.class, ServiceApiImpl.class})
public class ServiceApiControllerTest {

    private static final String PROFILE_HEADER = "profile";
    private static final String CHANNEL_ID_HEADER = "channel.Id";

    private static final String SDP_PROFILE = "SDP";
    private static final String SMS_CHANNEL = "SMS";
    private static final String MSISDN = "380999123624";
    private static final String SERVICE_CREATE_URL = "/DSEntityProvisioning/api/ActivationAndConfiguration/v2/service";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @MockBean
    private SagaService sagaService;

    @Captor
    private ArgumentCaptor<SagaTransaction> sagaTransactionCaptor;

    @MockBean
    private MessageSource messageSource;

    @MockBean
    private LocalizationMessageService localizationMessageService;

    @Autowired
    private ServiceApiImpl serviceApi;

    @Before
    public void setup() {
        // Setup MockMVC to use our Spring Configuration
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac)
            .build();
    }

    @Test
    public void shouldPassRequestAttributesToSagaTransaction() throws Exception {

        Service service = new Service();
        RelatedParty relatedParty = new RelatedParty();
        relatedParty.setId(MSISDN);
        //noinspection ArraysAsListWithZeroOrOneArgument
        service.setRelatedParty(asList(relatedParty));

        mockMvc.perform(post(SERVICE_CREATE_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .header(PROFILE_HEADER, SDP_PROFILE)
            .header(CHANNEL_ID_HEADER, SMS_CHANNEL)
            .content(objectMapper.writeValueAsBytes(service)));

        verify(sagaService).createNewSaga(sagaTransactionCaptor.capture());

        SagaTransaction sagaTransaction = sagaTransactionCaptor.getValue();

        ServletRequestAttributes requestAttributes =
            (ServletRequestAttributes) sagaTransaction.getContext().get(REQUEST_ATTRIBUTES_KEY);
        assertNotNull(requestAttributes);

        HttpServletRequest request = requestAttributes.getRequest();
        assertNotNull(request);

        assertEquals(SDP_PROFILE, request.getHeader(PROFILE_HEADER));
        assertEquals(SMS_CHANNEL, request.getHeader(CHANNEL_ID_HEADER));
    }
}