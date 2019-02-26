package com.icthh.xm.tmf.ms.activation.web.rest;

import com.codahale.metrics.annotation.Timed;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.service.SagaService;
import com.icthh.xm.tmf.ms.activation.web.rest.util.PaginationUtil;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class SagaTransactionResource {

    private final SagaService sagaService;

    @Timed
    @PreAuthorize("hasPermission({'sagaTransaction': #sagaTransaction}, 'ACTIVATION.TRANSACTION.CREATE')")
    @PostMapping("/transaction")
    public ResponseEntity<SagaTransaction> createSagaTransaction(@RequestBody SagaTransaction sagaTransaction) {
        return ResponseEntity.ok(sagaService.createNewSaga(sagaTransaction));
    }

    @Timed
    @PreAuthorize("hasPermission({'id': #id}, 'ACTIVATION.TRANSACTION.CONTINUE')")
    @PostMapping("/task/{id}/continue")
    public ResponseEntity<Void> continueTask(@PathVariable("id") String id, @RequestBody(required = false)
        Map<String, Object> taskContext) {
        sagaService.continueTask(id, taskContext);
        return ResponseEntity.ok().build();
    }

    @Timed
    @PostMapping("/transaction/{id}/cancel")
    @PreAuthorize("hasPermission({'id': #id}, 'ACTIVATION.TRANSACTION.CANCEL')")
    public ResponseEntity<Void> cancelSagaTransaction(@PathVariable("id") String id) {
        sagaService.cancelSagaTransaction(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transaction/{id}/events/{eventId}/retry")
    @PreAuthorize("hasPermission({'id': #id, 'eventId': #eventId}, 'ACTIVATION.TRANSACTION.RETRY')")
    public ResponseEntity<Void> retrySagaTransaction(@PathVariable("id") String id,
                                                                @PathVariable("eventId") String eventId) {
        sagaService.retrySagaEvent(id, eventId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasPermission({'id': #id, 'eventId': #eventId}, 'ACTIVATION.TRANSACTION.FIND_ALL')")
    public ResponseEntity<List<SagaTransaction>> getTransactions(Pageable pageable) {
        Page<SagaTransaction> page = sagaService.getAllTransaction(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/internal/transactions");
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/transactions/new")
    @PreAuthorize("hasPermission({'id': #id, 'eventId': #eventId}, 'ACTIVATION.TRANSACTION.FIND_NEW')")
    public ResponseEntity<List<SagaTransaction>> getNewTransactions(Pageable pageable) {
        Page<SagaTransaction> page = sagaService.getAllNewTransaction(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/internal/transactions/new");
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/transactions/{id}/events")
    @PreAuthorize("hasPermission({'id': #id, 'eventId': #eventId}, 'ACTIVATION.TRANSACTION.GET_EVENTS')")
    public ResponseEntity<List<SagaEvent>> getEventsByTransaction(@PathVariable("id") String id) {
        return ResponseEntity.ok(sagaService.getEventsByTransaction(id));
    }

    @GetMapping("/transactions/{id}/logs")
    @PreAuthorize("hasPermission({'id': #id, 'eventId': #eventId}, 'ACTIVATION.TRANSACTION.GET_LOGS')")
    public ResponseEntity<List<SagaLog>> getLogsByTransaction(@PathVariable("id") String id) {
        return ResponseEntity.ok(sagaService.getLogsByTransaction(id));
    }
}
