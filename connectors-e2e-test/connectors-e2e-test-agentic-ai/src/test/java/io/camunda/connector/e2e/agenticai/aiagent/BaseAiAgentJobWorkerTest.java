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
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_JOB_WORKER_ELEMENT_TEMPLATE_PATH;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_JOB_WORKER_ELEMENT_TEMPLATE_PROPERTIES;

import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentResponse;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.process.test.api.CamundaAssert;
import java.util.Map;
import java.util.function.Function;
import org.assertj.core.api.ThrowingConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

public abstract class BaseAiAgentJobWorkerTest extends BaseAiAgentTest {
  @Value("classpath:agentic-ai-ahsp-connectors.bpmn")
  protected Resource testProcess;

  @Override
  protected Resource testProcess() {
    return testProcess;
  }

  @Override
  protected String elementTemplatePath() {
    return AI_AGENT_JOB_WORKER_ELEMENT_TEMPLATE_PATH;
  }

  @Override
  protected Map<String, String> elementTemplateProperties() {
    return AI_AGENT_JOB_WORKER_ELEMENT_TEMPLATE_PROPERTIES;
  }

  @Override
  protected ElementTemplate elementTemplateWithModifications(
      String elementTemplatePath,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier) {
    final var elementTemplate =
        super.elementTemplateWithModifications(elementTemplatePath, elementTemplateModifier);

    // TODO JW remove this as soon as supported by the element template. Also, remove the attributes
    // from the BPMN file as we want to test that they are properly applied via template
    return elementTemplate.withoutProperty("outputElement").withoutProperty("outputCollection");
  }

  protected void assertAgentResponse(
      ZeebeTest zeebeTest, ThrowingConsumer<JobWorkerAgentResponse> assertions) {
    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasVariableSatisfies(
            AGENT_RESPONSE_VARIABLE,
            Map.class,
            agentResponseMap -> {
              // read with the connectors OM to include document deserialization support
              final var agentResponse =
                  objectMapper.convertValue(agentResponseMap, JobWorkerAgentResponse.class);
              assertions.accept(agentResponse);
            });
  }
}
