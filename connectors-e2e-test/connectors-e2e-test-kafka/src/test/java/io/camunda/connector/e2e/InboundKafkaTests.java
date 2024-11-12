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

import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.e2e.helper.KafkaTestProducer;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult.ProcessDefinitionIdentifier;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult.ProcessDefinitionVersion;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.client.api.search.response.ProcessDefinition;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Process;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
@CamundaSpringProcessTest
@ExtendWith(MockitoExtension.class)
public class InboundKafkaTests extends BaseKafkaTest {

  private static final String INTERMEDIATE_CATCH_EVENT_BPMN = "intermediate-catch-event.bpmn";

  @Mock private ProcessDefinition processDef;
  private KafkaTestProducer producer;

  @BeforeEach
  public void init() {
    producer = new KafkaTestProducer(getKafkaBrokers());
  }

  @AfterEach
  public void afterEach() {
    producer.close();
  }

  @Test
  void testKafkaIntermediateConnectorProcessWithJsonKey() {
    Map<String, Object> expectedJsonResponse =
        Map.of(
            "key", MESSAGE_KEY_JSON_AS_OBJECT,
            "rawValue", MESSAGE_VALUE,
            "value", MESSAGE_VALUE_AS_OBJECT,
            "headers", MESSAGE_HEADERS_AS_OBJECT);

    var model =
        replace(
            INTERMEDIATE_CATCH_EVENT_BPMN,
            BpmnFile.Replace.replace("kafkaBootstrapServers", getBootstrapServers()),
            BpmnFile.Replace.replace("kafkaTopic", TOPIC));

    mockProcessDefinition(model);
    processStateStore.update(
        new ProcessImportResult(
            Map.of(
                new ProcessDefinitionIdentifier(
                    processDef.getProcessDefinitionId(), processDef.getTenantId()),
                new ProcessDefinitionVersion(
                    processDef.getProcessDefinitionKey(), processDef.getVersion()))));

    AtomicBoolean kafkaProducerThreadRun =
        producer.startContinuousMessageSending(
            TOPIC, MESSAGE_KEY_JSON_AS_OBJECT, MESSAGE_VALUE, MESSAGE_HEADERS_AS_OBJECT);

    var bpmnTest = ZeebeTest.with(zeebeClient).deploy(model).createInstance();
    bpmnTest = bpmnTest.waitForProcessCompletion();

    kafkaProducerThreadRun.set(false);

    CamundaAssert.assertThat(bpmnTest.getProcessInstanceEvent())
        .hasVariable("keyResult", "keyJsonValue");
    CamundaAssert.assertThat(bpmnTest.getProcessInstanceEvent())
        .hasVariable("allResult", expectedJsonResponse);
  }

  @Test
  void testKafkaIntermediateConnectorProcessWithStringKey() {
    Map<String, Object> expectedJsonResponse =
        Map.of(
            "key",
            MESSAGE_KEY_STRING,
            "rawValue",
            MESSAGE_VALUE,
            "value",
            MESSAGE_VALUE_AS_OBJECT,
            "headers",
            MESSAGE_HEADERS_AS_OBJECT);

    var model =
        replace(
            "intermediate-catch-event.bpmn",
            BpmnFile.Replace.replace("kafkaBootstrapServers", getBootstrapServers()),
            BpmnFile.Replace.replace("kafkaTopic", TOPIC));

    mockProcessDefinition(model);
    processStateStore.update(
        new ProcessImportResult(
            Map.of(
                new ProcessDefinitionIdentifier(
                    processDef.getProcessDefinitionId(), processDef.getTenantId()),
                new ProcessDefinitionVersion(
                    processDef.getProcessDefinitionKey(), processDef.getVersion()))));

    AtomicBoolean kafkaProducerThreadRun =
        producer.startContinuousMessageSending(
            TOPIC, MESSAGE_KEY_STRING, MESSAGE_VALUE, MESSAGE_HEADERS_AS_OBJECT);

    var bpmnTest = ZeebeTest.with(zeebeClient).deploy(model).createInstance();
    bpmnTest = bpmnTest.waitForProcessCompletion();

    kafkaProducerThreadRun.set(false);

    CamundaAssert.assertThat(bpmnTest.getProcessInstanceEvent())
        .hasVariable("allResult", expectedJsonResponse);
  }

  private void mockProcessDefinition(BpmnModelInstance model) {
    when(camundaOperateClient.getProcessModel(1)).thenReturn(model);
    when(processDef.getProcessDefinitionKey()).thenReturn(1L);
    when(processDef.getTenantId()).thenReturn(zeebeClient.getConfiguration().getDefaultTenantId());
    when(processDef.getProcessDefinitionId())
        .thenReturn(model.getModelElementsByType(Process.class).stream().findFirst().get().getId());
  }
}
