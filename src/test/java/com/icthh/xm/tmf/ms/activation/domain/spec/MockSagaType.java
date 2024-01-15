package com.icthh.xm.tmf.ms.activation.domain.spec;

import com.icthh.xm.tmf.ms.activation.domain.SagaType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MockSagaType implements SagaType {

    private final String typeKey;

    @Override
    public String getSpecificationVersion() {
        return "0";
    }

    public static MockSagaType fromTypeKey(String typeKey) {
        return new MockSagaType(typeKey);
    }
}
