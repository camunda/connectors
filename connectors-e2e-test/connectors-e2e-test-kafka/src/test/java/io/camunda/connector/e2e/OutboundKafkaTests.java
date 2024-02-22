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
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.e2e.helper.KafkaTestConsumer;
import io.camunda.connector.kafka.inbound.KafkaInboundMessage;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.spring.test.ZeebeSpringTest;
import java.io.File;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ZeebeSpringTest
@ExtendWith(MockitoExtension.class)
public class OutboundKafkaTests extends BaseKafkaTest {
  private static final String ELEMENT_TEMPLATE_PATH =
      "../../connectors/kafka/element-templates/kafka-outbound-connector.json";
  private static final String OUTBOUND_RESULT_EXPRESSION =
      "={partitionResponse:partition, topicResult:topic}";

  private KafkaTestConsumer testConsumer;

  @BeforeEach
  public void beforeEach() {
    testConsumer =
        new KafkaTestConsumer(kafkaContainer.getBootstrapServers(), TEST_GROUP_ID, TOPIC);
  }

  @AfterEach
  public void afterEach() {
    if (testConsumer != null) {
      testConsumer.close();
    }
  }

  @Test
  void testKafkaConnectorProcess() {

    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("authentication.username", "")
            .property("authentication.password", "")
            .property("topic.bootstrapServers", getBootstrapServers())
            .property("topic.topicName", TOPIC)
            .property("headers", "=" + HEADER_KEY_VALUE)
            .property("additionalProperties", "=" + ADDITIONAL_PROPERTIES_KEY_VALUE)
            .property("message.key", "=" + MESSAGE_KEY_JSON)
            .property("message.value", "=" + MESSAGE_VALUE)
            .property("resultExpression", OUTBOUND_RESULT_EXPRESSION)
            .writeTo(new File(tempDir, "template.json"));

    BpmnModelInstance model = getBpmnModelInstance("outboundKafkaTask");
    BpmnModelInstance updatedModel =
        getBpmnModelInstance(model, elementTemplate, "outboundKafkaTask");
    var bpmnTest = getZeebeTest(updatedModel);

    KafkaInboundMessage kafkaMessage = testConsumer.pollMessages(1, 1000).get(0);
    // validate kafka message
    assertThat(kafkaMessage.getKey().toString()).isEqualTo(MESSAGE_KEY_JSON);
    assertThat(kafkaMessage.getValue().toString()).isEqualTo(MESSAGE_VALUE);
    assertThat(kafkaMessage.getRawValue()).isEqualTo(MESSAGE_VALUE);
    // validate process variables
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariableWithValue("partitionResponse", 0);
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariableWithValue("topicResult", TOPIC);
  }

  private static BpmnModelInstance getBpmnModelInstance(final String serviceTaskName) {
    return Bpmn.createProcess()
        .executable()
        .startEvent()
        .serviceTask(serviceTaskName)
        .endEvent()
        .done();
  }

  private ZeebeTest getZeebeTest(final BpmnModelInstance updatedModel) {
    return ZeebeTest.with(zeebeClient)
        .deploy(updatedModel)
        .createInstance()
        .waitForProcessCompletion();
  }

  private BpmnModelInstance getBpmnModelInstance(
      final BpmnModelInstance model, final File elementTemplate, final String taskName) {
    return new BpmnFile(model)
        .writeToFile(new File(tempDir, "test.bpmn"))
        .apply(elementTemplate, taskName, new File(tempDir, "result.bpmn"));
  }
}
