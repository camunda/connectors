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
package io.camunda.connector.e2e;

import static io.camunda.connector.e2e.BpmnFile.replace;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult.ProcessDefinitionIdentifier;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult.ProcessDefinitionVersion;
import io.camunda.connector.runtime.inbound.state.ProcessStateStore;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Process;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@ExtendWith(MockitoExtension.class)
public class RabbitMqInboundStartEventTests extends BaseRabbitMqTest {

  private static final String QUEUE_NAME = "testQueue";
  private static final String EXCHANGE_NAME = "testExchange";
  private static final String ROUTING_KEY = "testRoutingKey";
  private static String PORT;
  private static RabbitMQContainer rabbitMQContainer;
  private static ConnectionFactory factory;

  @Autowired ProcessStateStore processStateStore;

  @BeforeAll
  public static void setup() throws IOException, TimeoutException {
    rabbitMQContainer =
        new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.7.25-management-alpine"));
    rabbitMQContainer.start();
    PORT = String.valueOf(rabbitMQContainer.getAmqpPort());
    factory = new ConnectionFactory();
    factory.setHost(rabbitMQContainer.getHost());
    factory.setPort(rabbitMQContainer.getAmqpPort());
    factory.setUsername(rabbitMQContainer.getAdminUsername());
    factory.setPassword(rabbitMQContainer.getAdminPassword());

    try (Connection connection = factory.newConnection();
        Channel channel = connection.createChannel()) {
      channel.queueDeclare(QUEUE_NAME, true, false, false, null);
      channel.exchangeDeclare(EXCHANGE_NAME, "direct", true);
      channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY);
    }
  }

  @AfterAll
  public static void tearDown() {
    rabbitMQContainer.stop();
  }

  @BeforeEach
  public void cleanQueue() throws IOException, TimeoutException {
    try (Connection connection = factory.newConnection();
        Channel channel = connection.createChannel()) {
      // Purge the queue to ensure it is empty before conducting the test
      channel.queuePurge(QUEUE_NAME);
    }
  }

  @Test
  public void credentialsAuthenticationReceiveMessageTest() throws Exception {
    var model =
        replace(
            INTERMEDIATE_CATCH_EVENT_BPMN,
            BpmnFile.Replace.replace("rabbitMqAuthType", "credentials"),
            BpmnFile.Replace.replace("rabbitMqUserName", rabbitMQContainer.getAdminUsername()),
            BpmnFile.Replace.replace("rabbitMqPassword", rabbitMQContainer.getAdminPassword()),
            BpmnFile.Replace.replace("rabbitMqPort", PORT));
    assertIntermediateCatchEventUsingModel(model);
  }

  @Test
  public void uriAuthenticationReceiveMessageTest() throws Exception {
    String uri =
        String.format(
            "amqp://%s:%s@localhost:%s/%%2f",
            rabbitMQContainer.getAdminUsername(), rabbitMQContainer.getAdminPassword(), PORT);

    var model =
        replace(
            INTERMEDIATE_CATCH_EVENT_BPMN,
            BpmnFile.Replace.replace("rabbitMqAuthType", "uri"),
            BpmnFile.Replace.replace("rabbitMqUri", uri));

    assertIntermediateCatchEventUsingModel(model);
  }

  private void assertIntermediateCatchEventUsingModel(BpmnModelInstance model) throws Exception {
    Object expectedJsonResponse =
        ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.readValue(
            "{\"message\":{\"consumerTag\":\"myConsumerTag\",\"body\":{\"foo\": {\"bar\": \"barValue\"}},\"properties\":{}}}",
            Object.class);

    processStateStore.update(mockProcessDefinition(model));

    var bpmnTest = getZeebeTest(model);
    postMessage();
    bpmnTest = bpmnTest.waitForProcessCompletion();

    CamundaAssert.assertThat(bpmnTest.getProcessInstanceEvent())
        .hasVariable("allResult", expectedJsonResponse);

    CamundaAssert.assertThat(bpmnTest.getProcessInstanceEvent())
        .hasVariable("partialResult", "barValue");
  }

  private void postMessage() throws Exception {
    try (Connection connection = factory.newConnection();
        Channel channel = connection.createChannel()) {
      byte[] messageBodyBytes = "{\"foo\": {\"bar\": \"barValue\"}}".getBytes();
      channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, null, messageBodyBytes);
    }
  }

  private ProcessImportResult mockProcessDefinition(BpmnModelInstance model) {
    when(operateClient.getProcessModel(1)).thenReturn(model);
    var bpmnId = model.getModelElementsByType(Process.class).stream().findFirst().get().getId();
    var tenantId = zeebeClient.getConfiguration().getDefaultTenantId();
    return new ProcessImportResult(
        Map.of(
            new ProcessDefinitionIdentifier(bpmnId, tenantId),
            new ProcessDefinitionVersion(1L, 1)));
  }
}
