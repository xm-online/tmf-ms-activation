package com.icthh.xm.tmf.ms.activation.web.rest;

import static com.google.common.collect.ImmutableBiMap.of;

import com.icthh.xm.tmf.ms.activation.api.ServiceApiDelegate;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.model.Service;
import com.icthh.xm.tmf.ms.activation.service.SagaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ServiceApiImpl implements ServiceApiDelegate {

    private static final String MSISDN = "msisdn";
    private final SagaService sagaService;

    @Override
    public ResponseEntity<Service> serviceCreate(Service service) {
        sagaService.createNewSaga(new SagaTransaction().setTypeKey(service.getType())
                                      .setContext(of(MSISDN, service.getRelatedParty().get(0).getId())));
        return ResponseEntity.ok(service);
    }
}
