package com.icthh.xm.tmf.ms.activation.web.rest;

import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.service.SagaService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<SagaTransaction> continueTask(@PathVariable("id") String id, @RequestBody(required = false)
        Map<String, Object> taskContext) {
        sagaService.continueTask(id, taskContext);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transaction/{id}/cancel")
    public ResponseEntity<SagaTransaction> cancelSagaTransaction(@PathVariable("id") String id) {
        sagaService.cancelSagaEvent(id);
        return ResponseEntity.ok().build();
    }

}
