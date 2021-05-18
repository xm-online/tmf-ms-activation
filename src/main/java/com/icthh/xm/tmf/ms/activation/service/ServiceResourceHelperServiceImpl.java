package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.commons.lep.LogicExtensionPoint;
import com.icthh.xm.commons.lep.spring.LepService;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.mapper.ServiceMapper;
import com.icthh.xm.tmf.ms.activation.model.v4.Service;
import com.icthh.xm.tmf.ms.activation.model.v4.ServiceCreate;
import com.icthh.xm.tmf.ms.activation.resolver.TransactionTypeKeyResolver;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@LepService(group = "service.resource")
@org.springframework.stereotype.Service
public class ServiceResourceHelperServiceImpl implements ServiceResourceHelperService {

    private final ServiceMapper serviceMapper;

    @Override
    @LogicExtensionPoint(value = "EnrichServiceResponse", resolver = TransactionTypeKeyResolver.class)
    public Service enrichServiceResponse(ServiceCreate serviceCreate, SagaTransaction sagaTransaction) {
        Service service = serviceMapper.serviceCreateToService(serviceCreate);
        service.setId(sagaTransaction.getId());
        return service;
    }
}
