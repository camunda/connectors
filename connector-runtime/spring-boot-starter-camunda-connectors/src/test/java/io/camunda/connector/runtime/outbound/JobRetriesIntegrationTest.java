/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.outbound;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.connector.api.error.ConnectorRetryExceptionBuilder;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.Keywords;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.outbound.JobRetriesIntegrationTest.CustomConfiguration;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.util.Arrays;
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
@CamundaSpringProcessTest
@ExtendWith(MockitoExtension.class)
public class JobRetriesIntegrationTest {

  private static final String bpmnProcessId = "test-process";
  private static final String testConnectorType = "io.camunda:connector-test:1";
  private static final String testRetryConnectorType = "io.camunda:connector-retry-test:1";
  @Autowired private CamundaClient camundaClient;
  @Autowired private OutboundConnectorFactory factory;

  @BeforeEach
  void init() {
    ((CountingConnectorFunction) factory.getInstance(testConnectorType)).resetCounter();
  }

  @Test
  void retryNumberProvided_connectorInvokedExactlyAsManyTimes() {
    // given
    deployProcessWithRetries(2, "PT1S");
    var function = (CountingConnectorFunction) factory.getInstance(testConnectorType);

    // when
    createProcessInstance();

    // then
    // should be invoked twice exactly
    await()
        .during(1, SECONDS)
        .atMost(5, SECONDS)
        .failFast(() -> function.counter > 2)
        .until(() -> function.counter == 2);
  }

  @Test
  void invalidBackoffValueProvided_connectorNotExecuted() {
    // given
    deployProcessWithRetries(3, "NOT_A_VALID_DURATION");
    var function = (CountingConnectorFunction) factory.getInstance(testConnectorType);

    // when
    createProcessInstance();

    // then
    await()
        .during(3, SECONDS)
        .failFast(() -> function.counter > 0)
        .until(() -> function.counter == 0);
  }

  @Test
  void noRetriesProvided_connectorIsInvoked3times() {
    var function = (CountingConnectorFunction) factory.getInstance(testConnectorType);
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(bpmnProcessId)
                .startEvent()
                .serviceTask()
                .zeebeJobType(testConnectorType)
                .endEvent()
                .done(),
            bpmnProcessId + ".bpmn")
        .send()
        .join();

    createProcessInstance();

    await()
        .during(1, SECONDS)
        .atMost(5, SECONDS)
        .failFast(() -> function.counter > 3)
        .until(() -> function.counter == 3);
  }

  @Test
  void retryExceptionThrown_connectorIsInvoked5times() {
    var retryFunction =
        (CountingRetryConnectorFunction) factory.getInstance(testRetryConnectorType);
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(bpmnProcessId)
                .startEvent()
                .serviceTask()
                .zeebeJobType(testRetryConnectorType)
                .endEvent()
                .done(),
            bpmnProcessId + ".bpmn")
        .send()
        .join();

    createProcessInstance();

    await()
        .during(1, SECONDS)
        .atMost(5, SECONDS)
        .failFast(() -> retryFunction.counter > 5)
        .until(() -> retryFunction.counter == 5);
  }

  private void deployProcessWithRetries(int retries, String backoff) {
    camundaClient
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
        .send()
        .join();
  }

  private ProcessInstanceEvent createProcessInstance() {
    return camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .send()
        .join();
  }

  public static class CountingConnectorFunction implements OutboundConnectorFunction {

    int counter = 0;

    @Override
    public Object execute(OutboundConnectorContext context) {
      counter++;
      throw new RuntimeException("test");
    }

    void resetCounter() {
      counter = 0;
    }
  }

  public static class CountingRetryConnectorFunction implements OutboundConnectorFunction {

    int counter = 0;

    @Override
    public Object execute(OutboundConnectorContext context) throws Exception {
      counter++;
      throw new ConnectorRetryExceptionBuilder()
          .message("Retry error")
          .errorCode("RETRY_ERROR")
          .retries(5 - counter)
          .build();
    }

    void resetCounter() {
      counter = 0;
    }
  }

  @Configuration
  public static class CustomConfiguration {

    private final OutboundConnectorFunction function = new CountingConnectorFunction();
    private final OutboundConnectorFunction retryFunction = new CountingRetryConnectorFunction();

    @Bean
    @Primary
    public OutboundConnectorFactory mockConnectorFactory() {
      var mock = Mockito.mock(OutboundConnectorFactory.class);
      when(mock.getConfigurations())
          .thenReturn(
              Arrays.asList(
                  new OutboundConnectorConfiguration(
                      testConnectorType,
                      new String[0],
                      testConnectorType,
                      OutboundConnectorFunction.class),
                  new OutboundConnectorConfiguration(
                      testRetryConnectorType,
                      new String[0],
                      testRetryConnectorType,
                      OutboundConnectorFunction.class)));
      when(mock.getInstance(testConnectorType)).thenReturn(function);
      when(mock.getInstance(testRetryConnectorType)).thenReturn(retryFunction);
      return mock;
    }
  }
}
