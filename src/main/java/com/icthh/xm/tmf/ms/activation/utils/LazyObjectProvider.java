package com.icthh.xm.tmf.ms.activation.utils;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.function.SingletonSupplier;

import java.util.function.Supplier;

@Component
public class LazyObjectProvider<T> implements Supplier<T> {

    private final Supplier<T> supplier;
    private final ObjectFactory<T> objectFactory;

    public LazyObjectProvider(ObjectFactory<T> factory) {
        this.objectFactory = factory;
        this.supplier = SingletonSupplier.of(factory::getObject);
    }

    @Override
    public T get() {
        return supplier.get();
    }
}
