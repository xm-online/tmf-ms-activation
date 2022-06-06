package com.icthh.xm.tmf.ms.activation.web.rest;

import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.ON_RETRY;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.SUSPENDED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_END;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_START;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.NEW;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icthh.xm.commons.i18n.error.web.ExceptionTranslator;
import com.icthh.xm.commons.i18n.spring.service.LocalizationMessageService;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaLogType;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState;
import com.icthh.xm.tmf.ms.activation.service.SagaService;
import java.util.List;
import java.util.Optional;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Slf4j
@RunWith(SpringRunner.class)
@WebMvcTest(controllers = SagaTransactionResource.class)
@ContextConfiguration(classes = {SagaTransactionResource.class, ExceptionTranslator.class})
@EnableSpringDataWebSupport
public class SagaTransactionResourceTest {

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @Before
    public void setup() {
        // Setup MockMVC to use our Spring Configuration
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac)
                                      .build();
    }

    @MockBean
    private SagaService sagaService;


    @MockBean
    private MessageSource messageSource;

    @MockBean
    private LocalizationMessageService localizationMessageService;

    @Test
    @SneakyThrows
    public void retrySagaTransaction() {
        mockMvc.perform(post("/api/internal/transaction/some-txid/events/some-eventId/retry")
                            .contentType(TestUtil.APPLICATION_JSON_UTF8))
               .andExpect(status().isOk());

        verify(sagaService).retrySagaEvent("some-txid", "some-eventId");
    }


    @Test
    @SneakyThrows
    public void getTransactions() {

        List<SagaTransaction> transactions = asList(tx("1", "TK", NEW), tx("2", "TK", NEW));
        PageRequest pageRequest = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "id"));
        when(sagaService.getAllTransaction(eq(pageRequest))).thenReturn(new PageImpl<>(transactions, pageRequest, 20L));

        mockMvc.perform(get("/api/internal/transactions?page=1&size=5&sort=id,desc")
                            .contentType(TestUtil.APPLICATION_JSON_UTF8))
               .andDo(print())
               .andExpect(jsonPath("$.[*].id").value(hasItem("1")))
               .andExpect(jsonPath("$.[*].id").value(hasItem("2")))
               .andExpect(jsonPath("$.[*].typeKey").value(hasItem("TK")))
               .andExpect(jsonPath("$.[*].sagaTransactionState").value(hasItem("NEW")))
               .andExpect(status().isOk());

        verify(sagaService).getAllTransaction(eq(pageRequest));
    }

    @Test
    @SneakyThrows
    public void getEventsByTransactions() {

        List<SagaEvent> events = asList(event("1", "TK", ON_RETRY), event("2", "TK", SUSPENDED));
        when(sagaService.getEventsByTransaction("5")).thenReturn(events);

        mockMvc.perform(get("/api/internal//transactions/{id}/events?page=1&size=5&sort=id,desc", "5")
                            .contentType(TestUtil.APPLICATION_JSON_UTF8))
               .andDo(print())
               .andExpect(jsonPath("$.[*].id").value(hasItem("1")))
               .andExpect(jsonPath("$.[*].id").value(hasItem("2")))
               .andExpect(jsonPath("$.[*].typeKey").value(hasItem("TK")))
               .andExpect(jsonPath("$.[*].status").value(hasItem("ON_RETRY")))
               .andExpect(jsonPath("$.[*].status").value(hasItem("SUSPENDED")))
               .andExpect(status().isOk());

        verify(sagaService).getEventsByTransaction(eq("5"));
    }

    @Test
    @SneakyThrows
    public void getEventById() {

        SagaEvent event = event("1", "TK", ON_RETRY);
        when(sagaService.getEventById("1")).thenReturn(Optional.of(event));

        mockMvc.perform(get("/api/internal/transaction/events/{id}", "1")
                .contentType(TestUtil.APPLICATION_JSON_UTF8))
            .andDo(print())
            .andExpect(jsonPath("$.id").value(equalTo("1")))
            .andExpect(jsonPath("$.typeKey").value(equalTo("TK")))
            .andExpect(jsonPath("$.status").value(equalTo("ON_RETRY")))
            .andExpect(status().isOk());

        verify(sagaService).getEventById(eq("1"));
    }

    @Test
    @SneakyThrows
    public void getLogsByTransactions() {

        List<SagaLog> events = asList(log(1L, "TK", EVENT_START), log(2L, "TK", EVENT_END));
        when(sagaService.getLogsByTransaction("5")).thenReturn(events);

        mockMvc.perform(get("/api/internal//transactions/{id}/logs?page=1&size=5&sort=id,desc", "5")
                            .contentType(TestUtil.APPLICATION_JSON_UTF8))
               .andDo(print())
               .andExpect(jsonPath("$.[*].id").value(hasItem(1)))
               .andExpect(jsonPath("$.[*].id").value(hasItem(2)))
               .andExpect(jsonPath("$.[*].eventTypeKey").value(hasItem("TK")))
               .andExpect(jsonPath("$.[*].logType").value(hasItem("EVENT_START")))
               .andExpect(jsonPath("$.[*].logType").value(hasItem("EVENT_END")))
               .andExpect(status().isOk());

        verify(sagaService).getLogsByTransaction(eq("5"));
    }

    public SagaLog log(Long id, String typeKey, SagaLogType logType) {
        return new SagaLog().setId(id).setEventTypeKey(typeKey).setLogType(logType);
    }

    public SagaEvent event(String id, String typeKey, SagaEvent.SagaEventStatus sagaEventType) {
        return new SagaEvent().setId(id).setTypeKey(typeKey).setStatus(sagaEventType);
    }

    public SagaTransaction tx(String id, String typeKey, SagaTransactionState sagaTransactionState) {
        return new SagaTransaction().setId(id).setTypeKey(typeKey).setSagaTransactionState(sagaTransactionState);
    }
}

