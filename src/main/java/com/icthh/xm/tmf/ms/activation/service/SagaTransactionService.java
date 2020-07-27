package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaTransactionService {

    private static final String KEY_PARAM = "key";

    public SagaTransaction createSagaTransaction(String serviceType, Map<String, Object> params) {
        final Map<String, Object> sagaContext = new HashMap<>(params);
        sagaContext.putAll(getAllHeaders());

        final SagaTransaction sagaTransaction = new SagaTransaction()
            .setTypeKey(serviceType)
            .setContext(sagaContext);

        Optional.ofNullable(params.get(KEY_PARAM))
            .map(Object::toString)
            .ifPresent(sagaTransaction::setKey);

        return sagaTransaction;
    }

    /**
     * If service processes LEP`s asynchronously, RequestContextHolder will not be able to get request
     * attributes from thread that executes LEP
     *
     * @return params context that will hold extracted headers
     */
    private Map<String, String> getAllHeaders() {
        final Map<String, String> params = new HashMap<>();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                params.put(headerName, request.getHeader(headerName));
            }
        }
        return params;
    }
}
