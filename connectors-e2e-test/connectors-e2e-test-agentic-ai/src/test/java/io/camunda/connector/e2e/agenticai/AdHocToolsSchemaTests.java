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

import static io.camunda.process.test.api.CamundaAssert.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.agenticai.tools.cache.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
public class AdHocToolsSchemaTests {

  @Autowired private CamundaClient camundaClient;
  @Autowired private ObjectMapper objectMapper;

  @Value("classpath:agentic-ai-connectors.bpmn")
  private Resource process;

  @Value("classpath:expected-schema-result.json")
  private Resource expectedSchemaResult;

  @Test
  void loadsAdHocToolsSchema() throws IOException {
    final var model = Bpmn.readModelFromFile(process.getFile());
    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(model)
            .createInstance(Map.of("action", "resolveSchema"))
            .waitForProcessCompletion();

    final var expectedSchemaMap = objectMapper.readValue(expectedSchemaResult.getFile(), Map.class);
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("resolvedSchema", expectedSchemaMap);
  }
}
