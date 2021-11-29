package com.icthh.xm.tmf.ms.activation.repository;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent_;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction_;
import java.time.Instant;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Root;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class ExtendedSagaTransactionRepositoryImpl implements ExtendedSagaTransactionRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public long countByTypeKeyAndEventStatusAndCreatedDateBefore(String typeKey, SagaEvent.SagaEventStatus eventStatus, Instant date) {
        CriteriaBuilder criteria = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = criteria.createQuery(Long.class);
        Root<SagaTransaction> transaction = query.from(SagaTransaction.class);
        Root<SagaEvent> event = query.from(SagaEvent.class);

        ParameterExpression<String> typeKeyParam = criteria.parameter(String.class);
        ParameterExpression<SagaEvent.SagaEventStatus> eventStatusParam = criteria.parameter(SagaEvent.SagaEventStatus.class);
        ParameterExpression<Instant> createDateParam = criteria.parameter(Instant.class);

        query
            .select(criteria.count(event.get(SagaEvent_.id)))
            .where(
                criteria.and(
                    criteria.equal(transaction.get(SagaTransaction_.id), event.get(SagaEvent_.transactionId)),
                    criteria.equal(transaction.get(SagaTransaction_.typeKey), typeKeyParam),
                    criteria.equal(event.get(SagaEvent_.status), eventStatusParam),
                    criteria.lessThan(event.get(SagaEvent_.createDate), createDateParam)
                )
            );

        return entityManager.createQuery(query)
            .setParameter(typeKeyParam, typeKey)
            .setParameter(eventStatusParam, eventStatus)
            .setParameter(createDateParam, date)
            .getSingleResult();
    }
}
