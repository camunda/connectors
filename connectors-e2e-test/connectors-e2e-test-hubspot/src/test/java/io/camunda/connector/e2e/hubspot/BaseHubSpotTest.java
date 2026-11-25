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
package io.camunda.connector.e2e.hubspot;

import io.camunda.client.CamundaClient;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.io.File;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Applies the HubSpot connector template to a service task, the same way the modeler does, so tests
 * run against the real template and its input order.
 */
public abstract class BaseHubSpotTest {

  protected static final String ELEMENT_TEMPLATE_PATH =
      "../../connectors/hubspot/element-templates/hubspot-outbound-connector.json";

  protected static final String TASK_ID = "hubspotTask";

  protected static final String ACCESS_TOKEN = "hubspot-private-app-token";

  // Operation dropdown values
  protected static final String CREATE_DEAL = "post-/crm/v3/objects/deals_create";
  protected static final String UPDATE_DEAL = "patch-/crm/v3/objects/deals/{dealId}_update";

  // Template property ids of the deal operations
  protected static final String CREATE_DEAL_PROPERTIES =
      "post-/crm/v3/objects/deals_create_properties";
  protected static final String CREATE_DEAL_ASSOCIATIONS =
      "post-/crm/v3/objects/deals_create_associations";
  protected static final String UPDATE_DEAL_ID =
      "patch-/crm/v3/objects/deals/{dealId}_update_path_dealId";
  protected static final String UPDATE_DEAL_PROPERTIES =
      "patch-/crm/v3/objects/deals/{dealId}_update_properties";

  // HubSpot returns object ids as strings, but processes may also hold them as numbers.
  protected static final String DEAL_ID = "38146553331";

  @TempDir File tempDir;

  @Autowired CamundaClient camundaClient;

  /** The template with a deal operation and a bearer token, without a reusable credential. */
  protected static ElementTemplate dealTemplate(String operation) {
    return ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
        .property("resourceType", "deals")
        .property("operationId", operation)
        .property("authentication.token", ACCESS_TOKEN);
  }

  /** Applies the template to a process with a start event, the service task and an end event. */
  protected BpmnModelInstance applyTemplate(ElementTemplate template) {
    return applyTemplate(
        template,
        Bpmn.createProcess().executable().startEvent().serviceTask(TASK_ID).endEvent().done());
  }

  /** Applies the template to the service task {@link #TASK_ID} of the given process. */
  protected BpmnModelInstance applyTemplate(ElementTemplate template, BpmnModelInstance model) {
    var templateFile = template.writeTo(new File(tempDir, "template.json"));
    return new BpmnFile(model)
        .writeToFile(new File(tempDir, "test.bpmn"))
        .apply(templateFile, TASK_ID, new File(tempDir, "result.bpmn"));
  }

  /** Changes the job type so that a test worker, not the connector runtime, gets the job. */
  protected static BpmnModelInstance withJobType(BpmnModelInstance model, String jobType) {
    ServiceTask task = model.getModelElementById(TASK_ID);
    task.getExtensionElements()
        .getElementsQuery()
        .filterByType(ZeebeTaskDefinition.class)
        .singleResult()
        .setType(jobType);
    return model;
  }

  protected ZeebeTest run(BpmnModelInstance model, Map<String, Object> variables) {
    return ZeebeTest.with(camundaClient).deploy(model).createInstance(variables);
  }
}
