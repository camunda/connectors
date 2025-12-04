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
import io.camunda.client.api.search.response.Incident;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.function.Supplier;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.agenticai.tools.process-definition.cache.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
public abstract class BaseAgenticAiTest {
  @Autowired protected CamundaClient camundaClient;
  @Autowired @ConnectorsObjectMapper protected ObjectMapper objectMapper;
  @Autowired protected ResourceLoader resourceLoader;
  @TempDir protected File tempDir;

  protected ZeebeTest createProcessInstance(
      BpmnModelInstance model, Map<String, Object> variables) {
    return deployModel(model).createInstance(variables);
  }

  protected ZeebeTest deployModel(BpmnModelInstance model) {
    return deployModel(model, 1);
  }

  protected ZeebeTest deployModel(BpmnModelInstance model, int expectedVersionCount) {
    final var process =
        model.getDefinitions().getRootElements().stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No process found in the model"));

    ZeebeTest zeebeTest = ZeebeTest.with(camundaClient).awaitCompleteTopology().deploy(model);

    await()
        .pollInSameThread()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              final var processDefinitions =
                  camundaClient
                      .newProcessDefinitionSearchRequest()
                      .filter(filter -> filter.processDefinitionId(process.getId()))
                      .send()
                      .join();

              assertThat(processDefinitions.items()).hasSize(expectedVersionCount);
            });

    return zeebeTest;
  }

  protected void assertIncident(ZeebeTest zeebeTest, ThrowingConsumer<Incident> assertion) {
    final var incidents =
        camundaClient
            .newIncidentSearchRequest()
            .filter(
                filter ->
                    filter.processInstanceKey(
                        zeebeTest.getProcessInstanceEvent().getProcessInstanceKey()))
            .send()
            .join();

    assertThat(incidents.items()).hasSize(1).first().satisfies(assertion);
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
}
