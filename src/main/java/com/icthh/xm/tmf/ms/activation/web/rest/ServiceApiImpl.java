package com.icthh.xm.tmf.ms.activation.web.rest;

import static org.apache.commons.collections.CollectionUtils.isNotEmpty;

import com.codahale.metrics.annotation.Timed;
import com.icthh.xm.tmf.ms.activation.api.ServiceApiDelegate;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.model.Service;
import com.icthh.xm.tmf.ms.activation.service.SagaService;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ServiceApiImpl implements ServiceApiDelegate {

    private static final String MSISDN = "msisdn";
    private static final String KEY = "key";
    private final SagaService sagaService;

    @Timed
    @Override
    public ResponseEntity<Service> serviceCreate(Service service) {

        Map<String, Object> params = new HashMap<>();
        if (isNotEmpty(service.getRelatedParty())) {
            params.put(MSISDN, service.getRelatedParty().get(0).getId());
        }
        if (isNotEmpty(service.getServiceCharacteristic())) {
            service.getServiceCharacteristic().forEach(ch -> params.put(ch.getName(), ch.getValue()));
        }
        Object key = params.get(KEY);
        sagaService.createNewSaga(new SagaTransaction().setTypeKey(service.getType())
                                                       .setContext(params)
                                                       .setKey(key != null ? key.toString() : null));
        return ResponseEntity.ok(service);
    }
}
