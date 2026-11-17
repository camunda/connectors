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

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.io.File;
import java.util.Map;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Applies the Orchestration Cluster API connector template to a one-task process, the same way the
 * modeler does, so tests run against the real template and its input order.
 */
public abstract class BaseOrchestrationTest {

  protected static final String ELEMENT_TEMPLATE_PATH =
      "../../connectors/orchestration/element-templates/orchestration-connector.json";

  protected static final String TASK_ID = "orchestrationTask";

  // Operation dropdown values
  protected static final String SEARCH = "/search";
  protected static final String GET_BY_KEY = "/";

  // A realistic Zeebe key: larger than an int, so it catches number formatting problems.
  protected static final String KEY = "2251799813685249";

  @TempDir File tempDir;

  @Autowired CamundaClient camundaClient;

  protected static ElementTemplate saasTemplate(String endpoint, String operation) {
    return ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
        .property("input.internal_endpoint", endpoint)
        .property("input.internal_operation", operation)
        .property("authentication.internal_deployment_type", "saas")
        .propertyByBinding("authentication.internal_region", "bru-2")
        .propertyByBinding("authentication.internal_cluster", "a1b2c3d4-e5f6")
        .propertyByBinding("authentication.clientId", "orchestration-client")
        .propertyByBinding("authentication.clientSecret", "orchestration-secret");
  }

  protected static ElementTemplate selfManagedTemplate(
      String endpoint, String operation, String baseUrl, String tokenEndpoint) {
    return ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
        .property("input.internal_endpoint", endpoint)
        .property("input.internal_operation", operation)
        .property("authentication.internal_deployment_type", "sm")
        .property("authentication.orchestration_base_url", baseUrl)
        .propertyByBinding("authentication.oauthTokenEndpoint", tokenEndpoint)
        .propertyByBinding("authentication.clientId", "orchestration-client")
        .propertyByBinding("authentication.clientSecret", "orchestration-secret")
        .propertyByBinding("authentication.audience", "orchestration-api")
        .propertyByBinding("authentication.scopes", "orchestration");
  }

  /** Applies the template to a start event, one service task and an end event. */
  protected BpmnModelInstance applyTemplate(ElementTemplate template) {
    var templateFile = template.writeTo(new File(tempDir, "template.json"));
    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask(TASK_ID).endEvent().done();
    return new BpmnFile(model)
        .writeToFile(new File(tempDir, "test.bpmn"))
        .apply(templateFile, TASK_ID, new File(tempDir, "result.bpmn"));
  }

  /**
   * Moves the given inputs to the end of the input list. The modeler does this to every input the
   * user edits, so this recreates a diagram where the user changed these fields after applying the
   * template.
   */
  protected static BpmnModelInstance moveInputsToEnd(BpmnModelInstance model, String... targets) {
    var ioMapping = extension(model, ZeebeIoMapping.class);
    for (var target : targets) {
      var input =
          ioMapping.getInputs().stream()
              .filter(i -> target.equals(i.getTarget()))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("No input with target " + target));
      var moved = model.newInstance(ZeebeInput.class);
      moved.setSource(input.getSource());
      moved.setTarget(input.getTarget());
      ioMapping.removeChildElement(input);
      ioMapping.addChildElement(moved);
    }
    return model;
  }

  /** Changes the job type so that a test worker, not the connector runtime, gets the job. */
  protected static BpmnModelInstance withJobType(BpmnModelInstance model, String jobType) {
    extension(model, ZeebeTaskDefinition.class).setType(jobType);
    return model;
  }

  protected ZeebeTest run(BpmnModelInstance model, Map<String, Object> variables) {
    return ZeebeTest.with(camundaClient).deploy(model).createInstance(variables);
  }

  private static <T extends ModelElementInstance> T extension(
      BpmnModelInstance model, Class<T> type) {
    ServiceTask task = model.getModelElementById(TASK_ID);
    return task.getExtensionElements().getElementsQuery().filterByType(type).singleResult();
  }
}
