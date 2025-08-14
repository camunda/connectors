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

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AGENT_RESPONSE_VARIABLE;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_TASK_ID;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.BaseAgenticAiTest;
import io.camunda.connector.e2e.agenticai.CamundaDocumentTestConfiguration;
import io.camunda.process.test.api.CamundaAssert;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.assertj.core.api.ThrowingConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@WireMockTest
@Import(CamundaDocumentTestConfiguration.class)
public abstract class BaseAiAgentTest extends BaseAgenticAiTest {

  @Autowired protected ResourceLoader resourceLoader;

  protected abstract Resource testProcess();

  protected abstract String elementTemplatePath();

  protected abstract Map<String, String> elementTemplateProperties();

  protected ZeebeTest createProcessInstance(Map<String, Object> variables) throws IOException {
    return createProcessInstance(e -> e, variables);
  }

  protected ZeebeTest createProcessInstance(
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      Map<String, Object> variables)
      throws IOException {
    return createProcessInstance(testProcess(), elementTemplateModifier, variables);
  }

  protected ZeebeTest createProcessInstance(
      Resource process,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      Map<String, Object> variables)
      throws IOException {
    var elementTemplate = ElementTemplate.from(elementTemplatePath());
    elementTemplateProperties().forEach(elementTemplate::property);
    elementTemplate = elementTemplateModifier.apply(elementTemplate);

    final var elementTemplateFile = elementTemplate.writeTo(new File(tempDir, "template.json"));
    final var updatedModel =
        new BpmnFile(process.getFile())
            .apply(elementTemplateFile, AI_AGENT_TASK_ID, new File(tempDir, "updated.bpmn"));

    return deployModel(updatedModel).createInstance(variables);
  }

  protected void assertAgentResponse(
      ZeebeTest zeebeTest, ThrowingConsumer<AgentResponse> assertions) {
    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasVariableSatisfies(
            AGENT_RESPONSE_VARIABLE,
            Map.class,
            agentResponseMap -> {
              // read with the connectors OM to include document deserialization support
              final var agentResponse =
                  objectMapper.convertValue(agentResponseMap, AgentResponse.class);
              assertions.accept(agentResponse);
            });
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
