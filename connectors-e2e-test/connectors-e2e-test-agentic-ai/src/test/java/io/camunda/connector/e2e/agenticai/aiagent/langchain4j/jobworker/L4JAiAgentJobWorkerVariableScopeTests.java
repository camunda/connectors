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
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j.jobworker;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_JOB_WORKER_ELEMENT_TEMPLATE_PATH;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_TEXT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentResponse;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import java.io.File;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SlowTest
public class L4JAiAgentJobWorkerVariableScopeTests extends BaseL4JAiAgentJobWorkerTest {

  /** Last Camunda 8.8 compatible element template */
  private static final String LAST_8_8_ELEMENT_TEMPLATE_PATH =
      "../../connectors/agentic-ai/element-templates/versioned/agenticai-aiagent-job-worker-5.json";

  private static final String CUSTOM_RESULT_VARIABLE = "myAgentResult";

  @ParameterizedTest
  @ValueSource(
      strings = {AI_AGENT_JOB_WORKER_ELEMENT_TEMPLATE_PATH, LAST_8_8_ELEMENT_TEMPLATE_PATH})
  void agentVariableDoesNotLeakToGlobalScope(String elementTemplatePath) throws Exception {
    mockChatInteractions(
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(10, 20))
                        .build())
                .aiMessage(new AiMessage(HAIKU_TEXT))
                .build(),
            userSatisfiedFeedback()));

    final var updatedElementTemplate =
        elementTemplateWithModifications(
            elementTemplatePath,
            elementTemplate -> elementTemplate.property("resultVariable", CUSTOM_RESULT_VARIABLE));
    final var updatedElementTemplateFile =
        updatedElementTemplate.writeTo(new File(tempDir, "template.json"));
    final var updatedModel =
        modelWithModifications(testProcess.getFile(), updatedElementTemplateFile);

    final var zeebeTest =
        deployModel(updatedModel)
            .createInstance(Map.of("userPrompt", "Write a haiku about the sea"))
            .waitForProcessCompletion();

    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasVariableSatisfies(
            CUSTOM_RESULT_VARIABLE,
            Map.class,
            agentResponseMap -> {
              final var agentResponse =
                  objectMapper.convertValue(agentResponseMap, JobWorkerAgentResponse.class);
              assertThat(agentResponse.responseText()).isEqualTo(HAIKU_TEXT);
            });

    var globalVars =
        new CamundaDataSource(camundaClient)
            .findGlobalVariablesByProcessInstanceKey(
                zeebeTest.getProcessInstanceEvent().getProcessInstanceKey());

    assertThat(globalVars)
        .as("The 'agent' variable should not leak to the global process scope")
        .noneMatch(v -> v.getName().equals("agent"));
  }
}
