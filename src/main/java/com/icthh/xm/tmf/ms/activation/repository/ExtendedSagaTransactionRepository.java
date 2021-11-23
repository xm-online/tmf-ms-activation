package com.icthh.xm.tmf.ms.activation.repository;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import java.time.Instant;

public interface ExtendedSagaTransactionRepository {

    long countByTypeKeyAndEventStatusAndCreatedDateBefore(String typeKey, SagaEvent.SagaEventStatus eventStatus, Instant date);

}
