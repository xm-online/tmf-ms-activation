package com.icthh.xm.tmf.ms.activation;

import com.icthh.xm.tmf.ms.activation.config.SecurityBeanOverrideConfiguration;
import com.icthh.xm.tmf.ms.activation.service.LepContextCastIntTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
    SecurityBeanOverrideConfiguration.class,
    ActivationApp.class,
    LepContextCastIntTest.TestLepConfiguration.class})
@Category(AbstractSpringBootTest.class)
@Slf4j
@Profile("!dao-test")
public abstract class AbstractSpringBootTest {

}
