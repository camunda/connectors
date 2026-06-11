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
package io.camunda.connector.e2e.agenticai.aiagent.jobworker;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_TASK_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.AiAgentToolSpecifications;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.ScriptTask;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@SlowTest
public class AiAgentJobWorkerProcessMigrationTests extends BaseAiAgentJobWorkerTest {

  private static final String COMPLEX_TOOL_ID = "A_Complex_Tool";
  private static final String NEW_TOOL_ID = "A_New_Tool";

  private static final String FIRST_AI_MESSAGE =
      "The user asked me superflux calculate the product of 5 and 3 and to call the complex tool.";

  @Autowired private CamundaProcessTestContext processTestContext;

  @BeforeEach
  void updateTestFixtures() {
    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            FIRST_AI_MESSAGE,
            10,
            20,
            ToolCall.of("aaa111", "SuperfluxProduct", "{\"a\": 5, \"b\": 3}"),
            ToolCall.of("bbb222", COMPLEX_TOOL_ID, "{}")),
        Turn.text("Ok, all done.", 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());
  }

  @Test
  void updatesToolDefinitionsAfterMigration() throws IOException {
    final var zeebeTest = createProcessInstance();
    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasActiveElements(COMPLEX_TOOL_ID);

    final var firstRequest = OpenAiCompletionsRecordedConversation.recorded().requests().get(0);
    assertToolSpecifications(firstRequest);
    assertThat(firstRequest.toolNames()).doesNotContain(NEW_TOOL_ID);

    final var updatedProcessDefinition =
        deployModelUpdate(
            zeebeTest.getProcessInstanceEvent(),
            bpmnModel -> {
              final ScriptTask getDateAndTime = bpmnModel.getModelElementById("GetDateAndTime");
              getDateAndTime.getDocumentations().stream()
                  .findFirst()
                  .ifPresent(d -> d.setTextContent("Updated documentation"));

              final var scriptTask = bpmnModel.newInstance(ScriptTask.class, NEW_TOOL_ID).builder();
              scriptTask
                  .zeebeExpression("inputValue * 2")
                  .zeebeResultVariable("toolCallResult")
                  .zeebeInput(
                      "fromAi(toolCall.inputValue, \"The input value.\", \"number\")", "inputValue")
                  .documentation("A very new script task");

              final AdHocSubProcess adHocSubProcess = bpmnModel.getModelElementById("AI_Agent");
              adHocSubProcess.addChildElement(scriptTask.getElement());
            });

    migrateProcessInstance(zeebeTest.getProcessInstanceEvent(), updatedProcessDefinition);
    completeComplexTool();

    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasCompletedElements(COMPLEX_TOOL_ID)
        .isCompleted();

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(2);
    assertThat(recorded.lastRequest().toolNames())
        .hasSize(expectedTools().size() + 1)
        .contains(NEW_TOOL_ID);

    final var getDateAndTimeTool =
        recorded.lastRequest().toolDefinitions().stream()
            .filter(t -> t.name().equals(AiAgentToolSpecifications.GET_DATE_AND_TIME_TOOL_NAME))
            .findFirst()
            .orElseThrow();
    assertThat(getDateAndTimeTool.description()).isEqualTo("Updated documentation");
  }

  @Test
  void raisesIncidentWhenToolIsMissingAfterMigration() throws IOException {
    final var zeebeTest = createProcessInstance();
    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasActiveElements(COMPLEX_TOOL_ID);

    final var updatedProcessDefinition =
        deployModelUpdate(
            zeebeTest.getProcessInstanceEvent(),
            bpmnModel -> {
              final var getDateAndTime = bpmnModel.getModelElementById("GetDateAndTime");

              final AdHocSubProcess adHocSubProcess = bpmnModel.getModelElementById("AI_Agent");
              adHocSubProcess.removeChildElement(getDateAndTime);
            });

    migrateProcessInstance(zeebeTest.getProcessInstanceEvent(), updatedProcessDefinition);
    completeComplexTool();

    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent()).hasActiveIncidents();
    assertIncident(
        zeebeTest,
        incident -> {
          assertThat(incident.getElementId()).isEqualTo(AI_AGENT_TASK_ID);
          assertThat(incident.getErrorMessage())
              .contains("Removing or renaming existing tools is currently not supported.")
              .contains(
                  "Please re-add the following tools to continue agent execution: GetDateAndTime");
        });
  }

  @Test
  void raisesIncidentWhenGatewayToolDefinitionsChangedAfterMigration() throws IOException {
    final var zeebeTest = createProcessInstance();
    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasActiveElements(COMPLEX_TOOL_ID);

    final var updatedProcessDefinition =
        deployModelUpdate(
            zeebeTest.getProcessInstanceEvent(),
            bpmnModel -> {
              final var serviceTask =
                  bpmnModel
                      .newInstance(ServiceTask.class, NEW_TOOL_ID)
                      .builder()
                      .zeebeProperty("io.camunda.agenticai.gateway.type", "mcpClient")
                      .zeebeJobType("my-job-type");

              final AdHocSubProcess adHocSubProcess = bpmnModel.getModelElementById("AI_Agent");
              adHocSubProcess.addChildElement(serviceTask.getElement());
            });

    migrateProcessInstance(zeebeTest.getProcessInstanceEvent(), updatedProcessDefinition);
    completeComplexTool();

    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent()).hasActiveIncidents();
    assertIncident(
        zeebeTest,
        incident -> {
          assertThat(incident.getElementId()).isEqualTo(AI_AGENT_TASK_ID);
          assertThat(incident.getErrorMessage())
              .contains(
                  "Adding or removing gateway tool definitions to a running AI Agent is currently not supported.")
              .contains("Changes: mcpClient [added: A_New_Tool]");
        });
  }

  private ZeebeTest createProcessInstance() throws IOException {
    final var zeebeTest =
        createProcessInstance(
            testProcess,
            e -> e,
            Map.of(
                "userPrompt",
                "Calculate the superflux product of 5 and 3 and call the complex tool."));

    assertThat(zeebeTest.getProcessInstanceEvent().getVersion()).isEqualTo(1);

    return zeebeTest;
  }

  private ProcessDefinition deployModelUpdate(
      ProcessInstanceEvent processInstanceEvent, Consumer<BpmnModelInstance> modelUpdater) {
    final var updatedModel =
        Bpmn.readModelFromStream(
            new ByteArrayInputStream(
                camundaClient
                    .newProcessDefinitionGetXmlRequest(
                        processInstanceEvent.getProcessDefinitionKey())
                    .execute()
                    .getBytes()));

    modelUpdater.accept(updatedModel);
    deployModel(updatedModel, 2);

    final var processDefinitions =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(filter -> filter.processDefinitionId(processInstanceEvent.getBpmnProcessId()))
            .sort(sort -> sort.version().asc())
            .execute();

    assertThat(processDefinitions.items()).hasSize(2);

    final var updatedProcessDefinition = processDefinitions.items().get(1);
    assertThat(updatedProcessDefinition.getVersion()).isEqualTo(2);

    return updatedProcessDefinition;
  }

  private void migrateProcessInstance(
      ProcessInstanceEvent processInstanceEvent, ProcessDefinition updatedProcessDefinition) {
    camundaClient
        .newMigrateProcessInstanceCommand(processInstanceEvent.getProcessInstanceKey())
        .migrationPlan(updatedProcessDefinition.getProcessDefinitionKey())
        .addMappingInstruction("AI_Agent", "AI_Agent")
        .addMappingInstruction("On_Message_Event", "On_Message_Event")
        .addMappingInstruction(COMPLEX_TOOL_ID, COMPLEX_TOOL_ID)
        .execute();
  }

  private void completeComplexTool() {
    processTestContext
        .mockJobWorker("a-complex-tool")
        .thenComplete(Map.of("toolCallResult", "Tool completed after migration"));
  }
}
