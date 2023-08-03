package io.camunda.connector.runtime.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.operate.CamundaOperateClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = TestConnectorRuntimeApplication.class)
@TestPropertySource(properties = {"camunda.connector.polling.enabled=true"})
public class OperateClientAvailabilityTest {

  @Autowired
  private ApplicationContext applicationContext;

  @Test
  void noExtraProperties_operateClientAvailable() {
    // Operate client is available
    assertThat(applicationContext.getBeansOfType(CamundaOperateClient.class)).hasSize(1);
  }
}
