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

import static org.mockito.Mockito.when;

import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionSearch;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

public abstract class BaseRabbitMqTest {
  protected static final String OUTBOUND_ELEMENT_TEMPLATE_PATH =
      "../../connectors/rabbitmq/element-templates/rabbitmq-outbound-connector.json";

  protected static final String INBOUND_START_EVENT_ELEMENT_TEMPLATE_PATH =
      "../../connectors/rabbitmq/element-templates/rabbitmq-inbound-connector-start-event.json";

  protected static final String INBOUND_MESSAGE_START_ELEMENT_TEMPLATE_PATH =
      "../../connectors/rabbitmq/element-templates/rabbitmq-inbound-connector-message-start.json";

  protected static final String INBOUND_INTERMEDIATE_ELEMENT_TEMPLATE_PATH =
      "../../connectors/rabbitmq/element-templates/rabbitmq-inbound-connector-intermediate.json";

  protected static final String INBOUND_BOUNDARY_ELEMENT_TEMPLATE_PATH =
      "../../connectors/rabbitmq/element-templates/rabbitmq-inbound-connector-boundary.json";

  @TempDir File tempDir;

  @Autowired ZeebeClient zeebeClient;

  @MockBean ProcessDefinitionSearch processDefinitionSearch;

  @Autowired CamundaOperateClient camundaOperateClient;

  @LocalServerPort int serverPort;

  protected static BpmnModelInstance getBpmnModelInstance(final String serviceTaskName) {
    return Bpmn.createProcess()
        .executable()
        .startEvent()
        .serviceTask(serviceTaskName)
        .endEvent()
        .done();
  }

  @BeforeEach
  void beforeEach() {
    when(processDefinitionSearch.query()).thenReturn(Collections.emptyList());
  }

  protected ZeebeTest setupTestWithBpmnModel(String taskName, File elementTemplate) {
    BpmnModelInstance model = getBpmnModelInstance(taskName);
    BpmnModelInstance updatedModel = getBpmnModelInstance(model, elementTemplate, taskName);
    return getZeebeTest(updatedModel);
  }

  protected ZeebeTest getZeebeTest(final BpmnModelInstance updatedModel) {
    return ZeebeTest.with(zeebeClient)
        .deploy(updatedModel)
        .createInstance()
        .waitForProcessCompletion();
  }

  protected BpmnModelInstance getBpmnModelInstance(
      final BpmnModelInstance model, final File elementTemplate, final String taskName) {
    return new BpmnFile(model)
        .writeToFile(new File(tempDir, "test.bpmn"))
        .apply(elementTemplate, taskName, new File(tempDir, "result.bpmn"));
  }
}
