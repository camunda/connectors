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
package io.camunda.connector.e2e.agenticai.aiagent;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_ELEMENT_TEMPLATE_PATH;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_ELEMENT_TEMPLATE_PROPERTIES;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_TASK_ID;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.BaseAgenticAiTest;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

public abstract class BaseAiAgentTest extends BaseAgenticAiTest {
  @Autowired protected ResourceLoader resourceLoader;

  @Value("classpath:agentic-ai-connectors.bpmn")
  protected Resource testProcess;

  protected ZeebeTest createProcessInstance(Map<String, Object> variables) throws IOException {
    return createProcessInstance(e -> e, variables);
  }

  protected ZeebeTest createProcessInstance(
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      Map<String, Object> variables)
      throws IOException {
    return createProcessInstance(testProcess, elementTemplateModifier, variables);
  }

  protected ZeebeTest createProcessInstance(
      Resource process,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      Map<String, Object> variables)
      throws IOException {
    var elementTemplate = ElementTemplate.from(AI_AGENT_ELEMENT_TEMPLATE_PATH);
    AI_AGENT_ELEMENT_TEMPLATE_PROPERTIES.forEach(elementTemplate::property);
    elementTemplate = elementTemplateModifier.apply(elementTemplate);

    final var elementTemplateFile = elementTemplate.writeTo(new File(tempDir, "template.json"));
    final var updatedModel =
        new BpmnFile(process.getFile())
            .apply(elementTemplateFile, AI_AGENT_TASK_ID, new File(tempDir, "updated.bpmn"));

    return deployModel(updatedModel).createInstance(variables);
  }

  protected AgentResponse getAgentResponse(ZeebeTest zeebeTest) throws JsonProcessingException {
    final var agentVariableSearchResult =
        new CamundaDataSource(camundaClient)
                .findGlobalVariablesByProcessInstanceKey(
                    zeebeTest.getProcessInstanceEvent().getProcessInstanceKey())
                .stream()
                .filter(variable -> variable.getName().equals("agent"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Agent variable 'agent' not found"));

    if (agentVariableSearchResult.isTruncated()) {
      final var agentVariable =
          camundaClient
              .newVariableGetRequest(agentVariableSearchResult.getVariableKey())
              .send()
              .join();

      return objectMapper.readValue(agentVariable.getValue(), AgentResponse.class);
    }

    return objectMapper.readValue(agentVariableSearchResult.getValue(), AgentResponse.class);
  }

  protected Resource testFileResource(String filename) {
    return resourceLoader.getResource("classpath:__files/" + filename);
  }

  protected Supplier<String> testFileContent(String filename) {
    return () -> {
      try {
        return testFileResource(filename).getContentAsString(StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    };
  }

  protected Supplier<String> testFileContentBase64(String filename) {
    return () -> {
      try {
        return Base64.getEncoder()
            .encodeToString(testFileResource(filename).getContentAsByteArray());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    };
  }

  protected Map<String, Object> userSatisfiedFeedback() {
    return Map.of("userSatisfied", true);
  }

  protected Map<String, Object> userFollowUpFeedback(String followUp) {
    return Map.of("userSatisfied", false, "followUpUserPrompt", followUp);
  }
}
