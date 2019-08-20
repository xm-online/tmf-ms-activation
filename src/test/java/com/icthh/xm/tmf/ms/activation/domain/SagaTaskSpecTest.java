package com.icthh.xm.tmf.ms.activation.domain;

import com.icthh.xm.tmf.ms.activation.domain.spec.RetryPolicy;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.ArrayList;

public class SagaTaskSpecTest {

    @Test
    public void checkSagaTaskSpecIsCloneable() {
        SagaTaskSpec source = new SagaTaskSpec();
        // create mutable object fields
        source.setNext(new ArrayList<>());
        source.setDepends(new ArrayList<>());

        // clone
        SagaTaskSpec cloned = source.clone();

        // get all class fields with the all superclasses except java.lang.Object
        Field[] fields = SagaTaskSpec.class.getDeclaredFields();
        Class<?> currentClass = SagaTaskSpec.class.getSuperclass();
        // do not care about Object fields
        while (currentClass != Object.class) {
            Field[] superFields = currentClass.getDeclaredFields();
            Field[] tmpFields = new Field[fields.length + superFields.length];
            System.arraycopy(fields, 0, tmpFields, 0, fields.length);
            System.arraycopy(superFields, 0, tmpFields, fields.length, superFields.length);
            fields = tmpFields;
            currentClass = currentClass.getSuperclass();
        }

        // check that all known fields are cloned
        // all immutable but 'unknown' field types will fail as reminder
        for (Field field : fields) {
            // do not care about primitives, hover classes, enums and Strings
            if (!field.getType().isPrimitive()
                && field.getType() != org.slf4j.Logger.class // hello, lombok
                && field.getType() != String.class
                && field.getType() != Integer.class
                && field.getType() != Long.class
                && field.getType() != Boolean.class
                && field.getType() != RetryPolicy.class
                && !field.getName().startsWith("$") // hello, jacoco
            ) {
                // set private fields accessible
                field.setAccessible(true);
                Object srcValue = null;
                Object clonedValue = null;
                try {
                    srcValue = field.get(source);
                    clonedValue = field.get(cloned);
                } catch (Exception e) {
                    Assert.fail(e.getMessage());
                }
                // do not care about cloned fields
                if (srcValue == clonedValue) {
                    Assert.fail(MessageFormat.format("The field {} is not cloned", field.getName()));
                }
            }
        }
    }
}
