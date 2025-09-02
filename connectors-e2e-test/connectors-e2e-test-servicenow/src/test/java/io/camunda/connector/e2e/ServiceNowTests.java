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
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.test.ExternalSystem;
import io.camunda.connector.test.SystemIntegrationTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.webhook.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SystemIntegrationTest(with = ExternalSystem.ServiceNow)
@CamundaSpringProcessTest
public class ServiceNowTests {
  @Autowired private CamundaClient client;

  @Autowired Environment env;

  @TempDir File tempDir;

  String elementTemplatePath =
      "../../connectors/servicenow/element-templates/servicenow-connector.json";

  /** Pipeline context with fluent mutation methods */
  private class PipelineContext {
    final String processId;
    final String serviceTaskName;
    BpmnModelInstance model;
    BpmnModelInstance updatedModel;
    DeploymentEvent deploymentEvent;
    ProcessInstanceResult processInstanceResult;

    PipelineContext(String processId, String serviceTaskName) {
      this.processId = processId;
      this.serviceTaskName = serviceTaskName;
    }

    PipelineContext createModel() {
      this.model =
          Bpmn.createProcess(processId)
              .executable()
              .startEvent()
              .serviceTask(serviceTaskName)
              .endEvent()
              .done();
      return this;
    }

    PipelineContext updateModelWith(File with) {
      this.updatedModel =
          new BpmnFile(model)
              .writeToFile(new File(tempDir, java.util.UUID.randomUUID() + "-before.bpmn"))
              .apply(
                  with,
                  serviceTaskName,
                  new File(tempDir, java.util.UUID.randomUUID() + "-after.bpmn"));
      return this;
    }

    PipelineContext deploy() {
      this.deploymentEvent =
          client
              .newDeployResourceCommand()
              .addProcessModel(updatedModel, processId + ".bpmn")
              .send()
              .join();
      return this;
    }

    PipelineContext execute() {
      this.processInstanceResult =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(processId)
              .latestVersion()
              .withResult()
              .send()
              .join();
      return this;
    }
  }

  @Test
  void incident() {
    String serviceTaskName = "sn-incident-create";
    String processId = "snCreateIncidentProcess";
    String resultVariable = "sysId";

    var elementTemplate = ElementTemplate.from(elementTemplatePath);
    var filledTemplate =
        elementTemplate
            .property("snInstance", env.getProperty("SN_INSTANCE"))
            .property("operationGroup", "create")
            .property("targetTable", "incident")
            .property(
                "payload_incident",
                "={\"short_description\": \"Example incident created via API\"}")
            .property("authentication.username", env.getProperty("SN_USR"))
            .property("authentication.password", env.getProperty("SN_PWD"))
            .property("resultExpression", "={" + resultVariable + ": response.body.result.sys_id}")
            .writeTo(new File(tempDir, "servicenow-connector-incident.json"));

    PipelineContext pipeline =
        new PipelineContext(processId, serviceTaskName)
            .createModel()
            .updateModelWith(filledTemplate)
            .deploy()
            .execute();

    var variables = pipeline.processInstanceResult.getVariables();
    Assertions.assertNotNull(variables);
  }
}
