package com.icthh.xm.tmf.ms.activation.web.rest;

import com.codahale.metrics.annotation.Timed;
import com.icthh.xm.commons.permission.annotation.PrivilegeDescription;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.service.SagaService;
import com.icthh.xm.tmf.ms.activation.web.rest.util.PaginationUtil;
import io.github.jhipster.web.util.ResponseUtil;
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
import org.springframework.web.bind.annotation.PutMapping;
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
    @PrivilegeDescription("Privilege to create a new saga transaction")
    public ResponseEntity<SagaTransaction> createSagaTransaction(@RequestBody SagaTransaction sagaTransaction) {
        return ResponseEntity.ok(sagaService.createNewSaga(sagaTransaction));
    }

    @Timed
    @PreAuthorize("hasPermission({'id': #id}, 'ACTIVATION.TRANSACTION.CONTINUE')")
    @PostMapping("/task/{id}/continue")
    @PrivilegeDescription("Privilege to continue suspended task")
    public ResponseEntity<Void> continueTask(@PathVariable("id") String id, @RequestBody(required = false)
        Map<String, Object> taskContext) {
        sagaService.continueTask(id, taskContext);
        return ResponseEntity.ok().build();
    }

    @Timed
    @PostMapping("/transaction/{id}/cancel")
    @PreAuthorize("hasPermission({'id': #id}, 'ACTIVATION.TRANSACTION.CANCEL')")
    @PrivilegeDescription("Privilege to cancel saga transaction")
    public ResponseEntity<Void> cancelSagaTransaction(@PathVariable("id") String id) {
        sagaService.cancelSagaTransaction(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transaction/{id}/events/{eventId}/retry")
    @PreAuthorize("hasPermission({'id': #id, 'eventId': #eventId}, 'ACTIVATION.TRANSACTION.RETRY')")
    @PrivilegeDescription("Privilege to retry saga transaction")
    public ResponseEntity<Void> retrySagaTransaction(@PathVariable("id") String id,
                                                                @PathVariable("eventId") String eventId) {
        sagaService.retrySagaEvent(id, eventId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasPermission({'id': #id, 'eventId': #eventId}, 'ACTIVATION.TRANSACTION.FIND_ALL')")
    @PrivilegeDescription("Privilege to get all the transactions")
    public ResponseEntity<List<SagaTransaction>> getTransactions(Pageable pageable) {
        Page<SagaTransaction> page = sagaService.getAllTransaction(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/internal/transactions");
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/transactions/new")
    @PreAuthorize("hasPermission({'id': #id, 'eventId': #eventId}, 'ACTIVATION.TRANSACTION.FIND_NEW')")
    @PrivilegeDescription("Privilege to get all the new transactions")
    public ResponseEntity<List<SagaTransaction>> getNewTransactions(Pageable pageable) {
        Page<SagaTransaction> page = sagaService.getAllNewTransaction(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/internal/transactions/new");
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/transactions/{key}")
    @PreAuthorize("hasPermission({'key': #key}, 'ACTIVATION.TRANSACTION.GET_BY_KEY')")
    @PrivilegeDescription("Privilege to get transaction by key")
    public ResponseEntity<SagaTransaction> getTransactionByKey(@PathVariable("key") String key) {
        return ResponseEntity.ok(sagaService.getByKey(key));
    }

    @GetMapping("/transactions/{id}/events")
    @PreAuthorize("hasPermission({'id': #id, 'eventId': #eventId}, 'ACTIVATION.TRANSACTION.GET_EVENTS')")
    @PrivilegeDescription("Privilege to get all events by transaction")
    public ResponseEntity<List<SagaEvent>> getEventsByTransaction(@PathVariable("id") String id) {
        return ResponseEntity.ok(sagaService.getEventsByTransaction(id));
    }

    @PutMapping("/transactions/{id}/context")
    @PreAuthorize("hasPermission({'id': #id}, 'ACTIVATION.TRANSACTION.EVENTS.UPDATE_CONTEXT')")
    @PrivilegeDescription("Privilege to update saga transaction context")
    public void updateTransactionContext(@PathVariable("id") String id, @RequestBody Map<String, Object> context) {
        sagaService.updateTransactionContext(id, context);
    }

    @PutMapping("/transactions/events/{eventId}/context")
    @PreAuthorize("hasPermission({'eventId': #eventId}, 'ACTIVATION.TRANSACTION.EVENTS.UPDATE_CONTEXT')")
    @PrivilegeDescription("Privilege to update saga event context")
    public void updateContext(@PathVariable("eventId") String eventId, @RequestBody Map<String, Object> context) {
        sagaService.updateEventContext(eventId, context);
    }

    @GetMapping("/transactions/{id}/logs")
    @PreAuthorize("hasPermission({'id': #id, 'eventId': #eventId}, 'ACTIVATION.TRANSACTION.GET_LOGS')")
    @PrivilegeDescription("Privilege to get saga log records")
    public ResponseEntity<List<SagaLog>> getLogsByTransaction(@PathVariable("id") String id) {
        return ResponseEntity.ok(sagaService.getLogsByTransaction(id));
    }

    @Timed
    @PostMapping("/transaction/events/inqueue/resend")
    @PreAuthorize("hasPermission(null, 'ACTIVATION.TRANSACTION.IN_QUEUE_RESEND')")
    @PrivilegeDescription("Privilege to resend all events in state IN_QUEUE")
    public ResponseEntity<Void> resendAllInQueueEvents() {
        sagaService.resendEventsByStateInQueue();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/transactions/{id}")
    @PreAuthorize("hasPermission({'id': #id}, 'ACTIVATION.TRANSACTION.FIND_ONE')")
    public ResponseEntity<SagaTransaction> getOneById(@PathVariable("id") String id) {
        return ResponseUtil.wrapOrNotFound(sagaService.findTransactionById(id));
    }

}
