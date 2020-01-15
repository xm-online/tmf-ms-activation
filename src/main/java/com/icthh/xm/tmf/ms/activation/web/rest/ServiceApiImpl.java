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
import org.springframework.web.context.request.RequestContextHolder;

@Component
@RequiredArgsConstructor
public class ServiceApiImpl implements ServiceApiDelegate {

    private static final String MSISDN = "msisdn";
    private static final String REQUEST_ATTRIBUTES_KEY = "REQUEST_ATTRIBUTES";

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

        //if service processes LEP`s asynchronously, RequestContextHolder will not be able to get request
        //attributes from thread that will execute LEP
        params.put(REQUEST_ATTRIBUTES_KEY, RequestContextHolder.getRequestAttributes());

        sagaService.createNewSaga(new SagaTransaction().setTypeKey(service.getType()).setContext(params));
        return ResponseEntity.ok(service);
    }
}
