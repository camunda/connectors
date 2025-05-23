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
package io.camunda.connector.e2e.agenticai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.agenticai.tools.cache.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
public abstract class BaseAgenticAiTest {

  static final String PROCESS_DEFINITION_ID = "Agentic_AI_Connectors";
  static final String AI_AGENT_TASK_ID = "AI_Agent";

  @Autowired CamundaClient camundaClient;
  @Autowired ObjectMapper objectMapper;

  @Value("classpath:agentic-ai-connectors.bpmn")
  Resource process;

  protected ZeebeTest createProcessInstance(Map<String, Object> variables) throws IOException {
    return createProcessInstance(m -> m, variables);
  }

  protected ZeebeTest createProcessInstance(
      Function<BpmnModelInstance, BpmnModelInstance> modelModifier, Map<String, Object> variables)
      throws IOException {
    return deployModel(modelModifier).createInstance(variables);
  }

  protected ZeebeTest deployModel(Function<BpmnModelInstance, BpmnModelInstance> modelModifier)
      throws IOException {
    final var originalModel = Bpmn.readModelFromFile(process.getFile());
    final var modifiedModel = modelModifier.apply(originalModel);

    ZeebeTest zeebeTest =
        ZeebeTest.with(camundaClient).awaitCompleteTopology().deploy(modifiedModel);

    await()
        .pollInSameThread()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              final var processDefinitions =
                  camundaClient
                      .newProcessDefinitionSearchRequest()
                      .filter(filter -> filter.processDefinitionId(PROCESS_DEFINITION_ID))
                      .send()
                      .join();

              assertThat(processDefinitions.items()).hasSize(1);
            });

    return zeebeTest;
  }

  protected Function<BpmnModelInstance, BpmnModelInstance> withoutInputsMatching(
      Predicate<ZeebeInput> filter) {
    return (model) -> {
      final var inputs = getModelInputs(model);

      final var toRemove = inputs.stream().filter(filter).toList();
      inputs.removeAll(toRemove);

      return model;
    };
  }

  protected Function<BpmnModelInstance, BpmnModelInstance> withModifiedInputs(
      Map<String, Consumer<ZeebeInput>> modifiers) {
    return (model) -> {
      final var inputs = getModelInputs(model);

      for (final var input : inputs) {
        final var modifier = modifiers.get(input.getTarget());
        if (modifier != null) {
          modifier.accept(input);
        }
      }

      return model;
    };
  }

  protected Collection<ZeebeInput> getModelInputs(BpmnModelInstance model) {
    final ServiceTask aiAgentTask = model.getModelElementById(AI_AGENT_TASK_ID);
    return aiAgentTask.getSingleExtensionElement(ZeebeIoMapping.class).getInputs();
  }
}
