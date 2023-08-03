package io.camunda.connector.runtime.outbound;

import static io.camunda.zeebe.process.test.assertions.BpmnAssert.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.Keywords;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.outbound.JobRetriesIntegrationTest.CustomConfiguration;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.spring.test.ZeebeSpringTest;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class, CustomConfiguration.class},
    properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "camunda.connector.webhook.enabled=false",
        "camunda.connector.polling.enabled=false"
    })
@ZeebeSpringTest
@ExtendWith(MockitoExtension.class)
public class JobRetriesIntegrationTest {

  private final static String bpmnProcessId = "test-process";
  private final static String testConnectorType = "io.camunda:connector-test:1";

  public static class CountingConnectorFunction implements OutboundConnectorFunction {

    int counter = 0;

    @Override
    public Object execute(OutboundConnectorContext context) throws Exception {
      counter++;
      throw new RuntimeException("test");
    }

    void resetCounter() {
      counter = 0;
    }
  }

  @Configuration
  public static class CustomConfiguration {

    private final OutboundConnectorFunction function = new CountingConnectorFunction();

    @Bean
    @Primary
    public OutboundConnectorFactory mockConnectorFactory() {
      var mock = Mockito.mock(OutboundConnectorFactory.class);
      when(mock.getConfigurations()).thenReturn(Collections.singletonList(
          new OutboundConnectorConfiguration(testConnectorType, new String[0], testConnectorType,
              OutboundConnectorFunction.class)));
      when(mock.getInstance(testConnectorType)).thenReturn(function);
      return mock;
    }
  }

  @Autowired
  private ZeebeClient zeebeClient;

  @Autowired
  private OutboundConnectorFactory factory;

  @BeforeEach
  void init() {
    ((CountingConnectorFunction) factory.getInstance(testConnectorType)).resetCounter();
  }

  @Test
  void retryNumberProvided_connectorInvokedExactlyAsManyTimes() {
    // given
    deployProcessWithRetries(2, "PT1S");
    var function = (CountingConnectorFunction) factory.getInstance(testConnectorType);
    var recordStream = BpmnAssert.getRecordStream();

    // when
    var instance = createProcessInstance();

    // then
    await().atMost(2, SECONDS).untilAsserted(
        () -> {
          // need to reset it manually, as it is stored in ThreadLocal
          BpmnAssert.initRecordStream(recordStream);
          assertThat(instance).hasAnyIncidents();
        });
    Assertions.assertThat(function.counter).isEqualTo(2);
  }

  @Test
  void invalidBackoffValueProvided_connectorNotExecuted() {
    // given
    deployProcessWithRetries(3, "NOT_A_VALID_DURATION");
    var function = (CountingConnectorFunction) factory.getInstance(testConnectorType);
    var recordStream = BpmnAssert.getRecordStream();

    // when
    var instance = createProcessInstance();

    // then
    await().atMost(2, SECONDS).untilAsserted(
        () -> {
          // need to reset it manually, as it is stored in ThreadLocal
          BpmnAssert.initRecordStream(recordStream);
          assertThat(instance).hasAnyIncidents();
        });
    Assertions.assertThat(function.counter).isEqualTo(0);
  }

  @Test
  void noRetriesProvided_connectorIsInvoked3times() {
    var recordStream = BpmnAssert.getRecordStream();
    var function = (CountingConnectorFunction) factory.getInstance(testConnectorType);
    zeebeClient.newDeployResourceCommand().addProcessModel(
        Bpmn.createExecutableProcess(bpmnProcessId).startEvent()
            .serviceTask()
            .zeebeJobType(testConnectorType)
            .endEvent().done(),
        bpmnProcessId + ".bpmn").send().join();

    var instance = createProcessInstance();

    await().atMost(2, SECONDS).untilAsserted(
        () -> {
          // need to reset it manually, as it is stored in ThreadLocal
          BpmnAssert.initRecordStream(recordStream);
          assertThat(instance).hasAnyIncidents();
        });
    Assertions.assertThat(function.counter).isEqualTo(3);
  }

  private void deployProcessWithRetries(int retries, String backoff) {
    zeebeClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(bpmnProcessId)
                .startEvent()
                .serviceTask()
                .zeebeJobType(testConnectorType)
                .zeebeTaskHeader(Keywords.RETRY_BACKOFF_KEYWORD, backoff)
                .zeebeJobRetries(String.valueOf(retries))
                .endEvent()
                .done(),
            bpmnProcessId + ".bpmn")
        .send().join();
  }

  private ProcessInstanceEvent createProcessInstance() {
    return zeebeClient.newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .send().join();
  }
}
