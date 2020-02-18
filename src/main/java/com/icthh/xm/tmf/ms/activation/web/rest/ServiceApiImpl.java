package com.icthh.xm.tmf.ms.activation.web.rest;

import com.codahale.metrics.annotation.Timed;
import com.icthh.xm.tmf.ms.activation.api.ServiceApiDelegate;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.model.Service;
import com.icthh.xm.tmf.ms.activation.service.SagaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.collections.CollectionUtils.isNotEmpty;

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

        //if service processes LEP`s asynchronously, RequestContextHolder will not be able to get request
        //attributes from thread that will execute LEP
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (requestAttributes != null) {
            HttpServletRequest request = requestAttributes.getRequest();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                params.put(headerName, request.getHeader(headerName));
            }
        }

        Object key = params.get(KEY);
        sagaService.createNewSaga(new SagaTransaction().setTypeKey(service.getType())
            .setContext(params)
            .setKey(key != null ? key.toString() : null));
        return ResponseEntity.ok(service);
    }
}
