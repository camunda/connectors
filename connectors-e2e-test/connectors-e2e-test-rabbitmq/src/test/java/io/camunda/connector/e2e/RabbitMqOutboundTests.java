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

import static io.camunda.zeebe.process.test.assertions.BpmnAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.rabbitmq.outbound.RabbitMqResult;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.spring.test.ZeebeSpringTest;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ZeebeSpringTest
@ExtendWith(MockitoExtension.class)
public class RabbitMqOutboundTests extends BaseRabbitMqTest {
  private static final String QUEUE_NAME = "testQueue";
  private static final String EXCHANGE_NAME = "testExchange";
  private static final String ROUTING_KEY = "testRoutingKey";
  private static final String MESSAGE = "Hello, RabbitMQ!";
  private static final String VIRTUAL_HOST = "/";
  private static final String HOST_NAME = "localhost";
  private static String PORT;
  private static RabbitMQContainer rabbitMQContainer;
  private static ConnectionFactory factory;

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
  public void credentialsAuthenticationSendMessageTest() throws Exception {
    var elementTemplate =
        ElementTemplate.from(OUTBOUND_ELEMENT_TEMPLATE_PATH)
            .property("authentication.authType", "credentials")
            .property("authentication.userName", rabbitMQContainer.getAdminUsername())
            .property("authentication.password", rabbitMQContainer.getAdminPassword())
            .property("routing.exchange", EXCHANGE_NAME)
            .property("routing.routingKey", ROUTING_KEY)
            .property("routing.virtualHost", VIRTUAL_HOST)
            .property("routing.hostName", HOST_NAME)
            .property("routing.port", PORT)
            .property("message.body", MESSAGE)
            .property("resultVariable", "result")
            .writeTo(new File(tempDir, "template.json"));

    ZeebeTest bpmnTest = setupTestWithBpmnModel(elementTemplate);

    RabbitMqResult result = RabbitMqResult.success();
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariableWithValue("result", result);

    String receivedMessage = consumeMessage();

    if (receivedMessage == null) {
      fail("No message received within the expected timeout.");
    } else {
      assertEquals(MESSAGE, receivedMessage, "The sent message should match the received message");
    }
  }

  @Test
  public void uriAuthenticationSendMessageTest() throws Exception {
    String uri =
        String.format(
            "amqp://%s:%s@%s:%d/%s",
            rabbitMQContainer.getAdminUsername(),
            rabbitMQContainer.getAdminPassword(),
            rabbitMQContainer.getHost(),
            rabbitMQContainer.getAmqpPort(),
            "%2F");

    var elementTemplate =
        ElementTemplate.from(OUTBOUND_ELEMENT_TEMPLATE_PATH)
            .property("authentication.authType", "uri")
            .property("authentication.uri", uri)
            .property("routing.exchange", EXCHANGE_NAME)
            .property("routing.routingKey", ROUTING_KEY)
            .property("message.body", MESSAGE)
            .property("resultVariable", "result")
            .writeTo(new File(tempDir, "template.json"));

    ZeebeTest bpmnTest = setupTestWithBpmnModel(elementTemplate);

    RabbitMqResult result = RabbitMqResult.success();
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariableWithValue("result", result);

    String receivedMessage = consumeMessage();
    if (receivedMessage == null) {
      fail("No message received within the expected timeout.");
    } else {
      assertEquals(MESSAGE, receivedMessage, "The sent message should match the received message");
    }
  }

  private String consumeMessage() throws Exception {
    String receivedMessage = null;
    try (Connection connection = factory.newConnection();
        Channel channel = connection.createChannel()) {
      GetResponse response = channel.basicGet(QUEUE_NAME, true);
      if (response != null) {
        receivedMessage = new String(response.getBody(), StandardCharsets.UTF_8);
      }
    }
    return receivedMessage;
  }

  @Override
  protected BpmnModelInstance getBpmnModelInstance() {
    return Bpmn.createProcess().executable().startEvent().serviceTask(ELEMENT_ID).endEvent().done();
  }
}
