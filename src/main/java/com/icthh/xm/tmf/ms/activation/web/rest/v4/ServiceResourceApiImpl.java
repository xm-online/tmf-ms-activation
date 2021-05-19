package com.icthh.xm.tmf.ms.activation.web.rest.v4;

import static java.util.Optional.ofNullable;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.springframework.http.ResponseEntity.status;

import com.codahale.metrics.annotation.Timed;
import com.icthh.xm.commons.permission.annotation.PrivilegeDescription;
import com.icthh.xm.tmf.ms.activation.api.v4.ServiceResourceApiDelegate;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.model.v4.Service;
import com.icthh.xm.tmf.ms.activation.model.v4.ServiceCreate;
import com.icthh.xm.tmf.ms.activation.service.ResponseEnricherService;
import com.icthh.xm.tmf.ms.activation.service.SagaService;
import com.icthh.xm.tmf.ms.activation.service.SagaTransactionFactory;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ServiceResourceApiImpl implements ServiceResourceApiDelegate {

    private static final String MSISDN = "msisdn";
    private static final String STATE = "state";
    private static final String RELATED_PARTY_ID = "relatedParty.id";
    private static final String RELATED_PARTY_REFERRED_TYPE = "relatedParty.referredType";

    private final SagaService sagaService;
    private final SagaTransactionFactory sagaTransactionFactory;
    private final ResponseEnricherService responseEnricher;

    @Timed
    @PreAuthorize("hasPermission({'service': #service}, 'ACTIVATION.ACTION.SERVICE')")
    @PrivilegeDescription("Privilege to create service")
    @Override
    public ResponseEntity<Service> createService(ServiceCreate service) {
        Map<String, Object> params = new HashMap<>();
        if (isNotEmpty(service.getRelatedParty())) {
            service.getRelatedParty().stream()
                .filter(relatedParty -> MSISDN.equals(relatedParty.getAtReferredType()))
                .findAny()
                .ifPresent(relatedParty -> params.put(relatedParty.getAtReferredType(), relatedParty.getId()));

            service.getRelatedParty().stream()
                .findFirst()
                .ifPresent(relatedParty -> {
                        params.put(RELATED_PARTY_ID, relatedParty.getId());
                        params.put(RELATED_PARTY_REFERRED_TYPE, relatedParty.getAtReferredType());
                });
        }
        if (isNotEmpty(service.getServiceCharacteristic())) {
            service.getServiceCharacteristic().forEach(ch -> params.put(ch.getName(), ch.getValue()));
        }
        ofNullable(service.getState()).ifPresent(state -> params.put(STATE, state));

        SagaTransaction sagaTransaction =
            sagaTransactionFactory.createSagaTransaction(service.getServiceSpecification().getId(), params);
        SagaTransaction saga = sagaService.createNewSaga(sagaTransaction);

        Service createdService = responseEnricher.enrichServiceResponse(service, saga);

        return status(HttpStatus.CREATED).body(createdService);
    }
}
