package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.model.v4.Service;
import com.icthh.xm.tmf.ms.activation.model.v4.ServiceCreate;

public interface ServiceResourceHelperService {

    Service enrichServiceResponse(ServiceCreate serviceCreate, SagaTransaction sagaTransaction);
}
