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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_TASK_ID;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.BaseAgenticAiTest;
import io.camunda.connector.e2e.agenticai.CamundaDocumentTestConfiguration;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;

@WireMockTest
@Import(CamundaDocumentTestConfiguration.class)
public abstract class BaseAiAgentTest extends BaseAgenticAiTest {

  private JobWorker userFeedbackJobWorker;
  protected final AtomicInteger userFeedbackJobWorkerCounter = new AtomicInteger(0);
  protected final AtomicReference<Map<String, Object>> userFeedbackVariables =
      new AtomicReference<>(Collections.emptyMap());

  @BeforeEach
  void clearDocumentationStore() {
    InMemoryDocumentStore.INSTANCE.clear();
  }

  @BeforeEach
  void setupWireMock() {
    // WireMock returns the content type for the YAML file as application/json, so
    // we need to override the stub manually
    stubFor(
        get(urlPathEqualTo("/test.yaml"))
            .willReturn(
                aResponse()
                    .withBodyFile("test.yaml")
                    .withHeader("Content-Type", "application/yaml")));
  }

  @BeforeEach
  void openUserFeedbackJobWorker() {
    userFeedbackVariables.set(Collections.emptyMap());
    userFeedbackJobWorkerCounter.set(0);
    userFeedbackJobWorker =
        camundaClient
            .newWorker()
            .jobType("user_feedback")
            .handler(
                (client, job) -> {
                  userFeedbackJobWorkerCounter.incrementAndGet();
                  client
                      .newCompleteCommand(job.getKey())
                      .variables(userFeedbackVariables.get())
                      .send()
                      .join();
                })
            .open();
  }

  @AfterEach
  void closeUserFeedbackJobWorker() {
    if (userFeedbackJobWorker != null) {
      userFeedbackJobWorker.close();
    }
  }

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
    final var updatedElementTemplate =
        elementTemplateWithModifications(elementTemplatePath(), elementTemplateModifier);
    final var updatedElementTemplateFile =
        updatedElementTemplate.writeTo(new File(tempDir, "template.json"));
    final var updatedModel = modelWithModifications(process.getFile(), updatedElementTemplateFile);

    return deployModel(updatedModel).createInstance(variables);
  }

  protected ElementTemplate elementTemplateWithModifications(
      String elementTemplatePath,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier) {
    final var elementTemplate = ElementTemplate.from(elementTemplatePath);
    elementTemplateProperties().forEach(elementTemplate::property);
    return elementTemplateModifier.apply(elementTemplate);
  }

  protected BpmnModelInstance modelWithModifications(File model, File elementTemplate) {
    return new BpmnFile(model)
        .apply(elementTemplate, AI_AGENT_TASK_ID, new File(tempDir, "updated.bpmn"));
  }

  protected Map<String, Object> userSatisfiedFeedback() {
    return Map.of("userSatisfied", true);
  }

  protected Map<String, Object> userFollowUpFeedback(String followUp) {
    return Map.of("userSatisfied", false, "followUpUserPrompt", followUp);
  }

  protected ToolExecutionResult toolExecutionResult(String resultText) {
    return ToolExecutionResult.builder().resultText(resultText).build();
  }
}
