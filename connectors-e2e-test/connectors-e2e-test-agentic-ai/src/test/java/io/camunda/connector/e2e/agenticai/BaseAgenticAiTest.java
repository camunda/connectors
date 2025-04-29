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
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
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

  public static final String PROCESS_DEFINITION_ID = "Agentic_AI_Connectors";

  @Autowired CamundaClient camundaClient;
  @Autowired ObjectMapper objectMapper;

  @Value("classpath:agentic-ai-connectors.bpmn")
  Resource process;

  protected ZeebeTest createProcessInstance(Map<String, Object> variables) throws IOException {
    return deployModel().createInstance(variables);
  }

  protected ZeebeTest deployModel() throws IOException {
    final var model = Bpmn.readModelFromFile(process.getFile());

    ZeebeTest zeebeTest = ZeebeTest.with(camundaClient).awaitCompleteTopology().deploy(model);

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
}
