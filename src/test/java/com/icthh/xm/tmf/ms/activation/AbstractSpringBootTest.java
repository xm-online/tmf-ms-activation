package com.icthh.xm.tmf.ms.activation;

import com.icthh.xm.tmf.ms.activation.config.SecurityBeanOverrideConfiguration;
import com.icthh.xm.tmf.ms.activation.service.LepContextCastIntTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {
    SecurityBeanOverrideConfiguration.class,
    ActivationApp.class,
    LepContextCastIntTest.TestLepConfiguration.class})
@Tag("com.icthh.xm.tmf.ms.activation.AbstractSpringBootTest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
@Profile("!dao-test")
public abstract class AbstractSpringBootTest {

}
