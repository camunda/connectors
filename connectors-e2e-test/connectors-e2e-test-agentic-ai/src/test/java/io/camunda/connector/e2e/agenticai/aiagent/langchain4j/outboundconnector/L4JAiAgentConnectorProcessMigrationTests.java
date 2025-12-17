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
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j.outboundconnector;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_TASK_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.connector.e2e.ZeebeTest;
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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@SlowTest
public class L4JAiAgentConnectorProcessMigrationTests extends BaseL4JAiAgentConnectorTest {

  private static final String AGENT_TOOLS_ID = "Agent_Tools";
  private static final String COMPLEX_TOOL_ID = "A_Complex_Tool";
  private static final String NEW_TOOL_ID = "A_New_Tool";

  @Autowired private CamundaProcessTestContext processTestContext;

  @BeforeEach
  void updateTestFixtures() {
    // make sure process exits after exiting the AHSP
    userFeedbackVariables.set(userSatisfiedFeedback());

    mockModelInteractions();
  }

  @Test
  void updatesToolDefinitionsAfterMigration() throws IOException {
    final var zeebeTest = createProcessInstance();
    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasActiveElements(COMPLEX_TOOL_ID);

    assertToolSpecifications(chatRequestCaptor.getValue());
    assertThat(chatRequestCaptor.getValue().toolSpecifications())
        .extracting(ToolSpecification::name)
        .doesNotContain(NEW_TOOL_ID);

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

              final AdHocSubProcess adHocSubProcess = bpmnModel.getModelElementById(AGENT_TOOLS_ID);
              adHocSubProcess.addChildElement(scriptTask.getElement());
            });

    migrateProcessInstance(zeebeTest.getProcessInstanceEvent(), updatedProcessDefinition);
    completeComplexTool();

    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasCompletedElements(COMPLEX_TOOL_ID)
        .isCompleted();

    assertThat(chatRequestCaptor.getAllValues()).hasSize(2);
    assertThat(chatRequestCaptor.getValue().toolSpecifications())
        .hasSize(expectedToolSpecifications().size() + 1)
        .extracting(ToolSpecification::name)
        .contains(NEW_TOOL_ID);
    assertThat(chatRequestCaptor.getValue().toolSpecifications())
        .filteredOn(t -> t.name().equals("GetDateAndTime"))
        .first()
        .satisfies(
            toolSpec -> assertThat(toolSpec.description()).isEqualTo("Updated documentation"));
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

              final AdHocSubProcess adHocSubProcess = bpmnModel.getModelElementById(AGENT_TOOLS_ID);
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

              final AdHocSubProcess adHocSubProcess = bpmnModel.getModelElementById(AGENT_TOOLS_ID);
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

  private void mockModelInteractions() {
    when(chatModel.chat(chatRequestCaptor.capture()))
        .thenReturn(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .tokenUsage(new TokenUsage(10, 20))
                        .build())
                .aiMessage(
                    new AiMessage(
                        "The user asked me superflux calculate the product of 5 and 3 and to call the complex tool.",
                        List.of(
                            ToolExecutionRequest.builder()
                                .id("aaa111")
                                .name("SuperfluxProduct")
                                .arguments("{\"a\": 5, \"b\": 3}")
                                .build(),
                            ToolExecutionRequest.builder()
                                .id("bbb222")
                                .name(COMPLEX_TOOL_ID)
                                .arguments("{}")
                                .build())))
                .build(),
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(11, 22))
                        .build())
                .aiMessage(new AiMessage("Ok, all done."))
                .build());
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
        .addMappingInstruction(AI_AGENT_TASK_ID, AI_AGENT_TASK_ID)
        .addMappingInstruction(AGENT_TOOLS_ID, AGENT_TOOLS_ID)
        .addMappingInstruction(COMPLEX_TOOL_ID, COMPLEX_TOOL_ID)
        .execute();
  }

  private void completeComplexTool() {
    processTestContext
        .mockJobWorker("a-complex-tool")
        .thenComplete(Map.of("toolCallResult", "Tool completed after migration"));
  }
}
