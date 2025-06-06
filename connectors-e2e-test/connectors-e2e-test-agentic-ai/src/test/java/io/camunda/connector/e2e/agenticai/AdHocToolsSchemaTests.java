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

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.client.api.search.response.Variable;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.test.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

@SlowTest
public class AdHocToolsSchemaTests extends BaseAgenticAiTest {

  @Value("classpath:expected-schema-result.json")
  private Resource expectedSchemaResult;

  @Test
  void loadsAdHocToolsSchema() throws IOException {
    final var zeebeTest =
        createProcessInstance(Map.of("action", "resolveSchema")).waitForProcessCompletion();

    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasVariableNames("resolvedSchema");

    final Map<String, Object> expectedSchema =
        objectMapper.readValue(expectedSchemaResult.getFile(), new TypeReference<>() {});

    final var resolvedSchemaVariable = getResolvedSchemaVariable(zeebeTest);
    final Map<String, Object> resolvedSchema =
        objectMapper.readValue(resolvedSchemaVariable.getValue(), new TypeReference<>() {});

    assertThat(resolvedSchema).usingRecursiveComparison().isEqualTo(expectedSchema);
  }

  private Variable getResolvedSchemaVariable(ZeebeTest zeebeTest) {
    return new CamundaDataSource(camundaClient)
            .findGlobalVariablesByProcessInstanceKey(
                zeebeTest.getProcessInstanceEvent().getProcessInstanceKey())
            .stream()
            .filter(variable -> variable.getName().equals("resolvedSchema"))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Schema variable not found"));
  }
}
