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
package io.camunda.connector.e2e.agenticai.adhoctoolsschema;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AD_HOC_TOOLS_SCHEMA_ELEMENT_TEMPLATE_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.client.api.search.response.Variable;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.BaseAgenticAiTest;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.springframework.core.io.Resource;

abstract class BaseAdHocToolsSchemaTests extends BaseAgenticAiTest {

  protected static final String SCHEMA_RESOLVER_ELEMENT_ID = "Resolve_Schema";
  protected static final String RESOLVED_SCHEMA_VARIABLE = "resolvedSchema";

  protected void testAdHocToolSchema(Resource process, Resource expectedResult) throws IOException {
    var elementTemplate =
        ElementTemplate.from(AD_HOC_TOOLS_SCHEMA_ELEMENT_TEMPLATE_PATH)
            .property("data.containerElementId", "Agent_Tools")
            .property("resultVariable", RESOLVED_SCHEMA_VARIABLE)
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(process.getFile())
            .apply(elementTemplate, SCHEMA_RESOLVER_ELEMENT_ID, new File(tempDir, "updated.bpmn"));

    final var zeebeTest =
        createProcessInstance(updatedModel, Map.of("action", "resolveSchema"))
            .waitForProcessCompletion();

    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasVariableNames(RESOLVED_SCHEMA_VARIABLE);

    final Map<String, Object> expectedSchema =
        objectMapper.readValue(expectedResult.getFile(), new TypeReference<>() {});

    final var resolvedSchemaVariable = getResolvedSchemaVariable(zeebeTest);
    final Map<String, Object> resolvedSchema =
        objectMapper.readValue(resolvedSchemaVariable.getValue(), new TypeReference<>() {});

    assertThat(resolvedSchema).usingRecursiveComparison().isEqualTo(expectedSchema);
  }

  private Variable getResolvedSchemaVariable(ZeebeTest zeebeTest) {
    return new CamundaDataSource(camundaClient)
            .findVariablesByProcessInstanceKey(
                zeebeTest.getProcessInstanceEvent().getProcessInstanceKey())
            .stream()
            .filter(variable -> variable.getName().equals(RESOLVED_SCHEMA_VARIABLE))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Schema variable not found"));
  }
}
