package com.icthh.xm.tmf.ms.activation.web.rest;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.service.SagaService;
import com.icthh.xm.tmf.ms.activation.web.rest.util.PaginationUtil;
import io.swagger.annotations.ApiParam;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/transaction")
    public ResponseEntity<SagaTransaction> createSagaTransaction(@RequestBody SagaTransaction sagaTransaction) {
        return ResponseEntity.ok(sagaService.createNewSaga(sagaTransaction));
    }

    @PostMapping("/task/{id}/continue")
    public ResponseEntity<Void> continueTask(@PathVariable("id") String id, @RequestBody(required = false)
        Map<String, Object> taskContext) {
        sagaService.continueTask(id, taskContext);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transaction/{id}/cancel")
    public ResponseEntity<Void> cancelSagaTransaction(@PathVariable("id") String id) {
        sagaService.cancelSagaTransaction(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transaction/{id}/events/{eventId}/retry")
    public ResponseEntity<Void> retrySagaTransaction(@PathVariable("id") String id,
                                                                @PathVariable("eventId") String eventId) {
        sagaService.retrySagaEvent(id, eventId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<SagaTransaction>> getTransactions(Pageable pageable) {
        Page<SagaTransaction> page = sagaService.getAllTransaction(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/internal/transactions");
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/transactions/new")
    public ResponseEntity<List<SagaTransaction>> getNewTransactions(Pageable pageable) {
        Page<SagaTransaction> page = sagaService.getAllNewTransaction(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/internal/transactions/new");
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/transactions/{id}/events")
    public ResponseEntity<List<SagaEvent>> getEventsByTransaction(@PathVariable("id") String id) {
        return ResponseEntity.ok(sagaService.getEventsByTransaction(id));
    }

    @GetMapping("/transactions/{id}/logs")
    public ResponseEntity<List<SagaLog>> getLogsByTransaction(@PathVariable("id") String id) {
        return ResponseEntity.ok(sagaService.getLogsByTransaction(id));
    }
}
