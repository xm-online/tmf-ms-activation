package com.icthh.xm.tmf.ms.activation.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@UtilityClass
public class TransactionUtils {

    public static void executeIfCommited(Runnable onSuccess, Runnable onFailure) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_COMMITTED) {
                        onSuccess.run();
                    } else {
                        onFailure.run();
                    }
                }
            });
    }
}
