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
package io.camunda.connector.runtime.inbound.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.Variable;
import io.camunda.client.impl.search.response.ElementInstanceImpl;
import io.camunda.client.impl.search.response.SearchResponseImpl;
import io.camunda.client.impl.search.response.SearchResponsePageImpl;
import io.camunda.client.impl.search.response.VariableImpl;
import io.camunda.client.protocol.rest.ElementInstanceResult;
import io.camunda.client.protocol.rest.VariableResult;
import io.camunda.connector.runtime.core.inbound.ProcessInstanceClient;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessInstanceClientImplTest {

  @Mock private SearchQueryClient searchQueryClient;
  private ObjectMapper objectMapper;

  @BeforeEach
  public void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  public void testFetchFlowNodeInstanceByDefinitionKeyAndElementId() {
    ProcessInstanceClient processInstanceClient =
        new ProcessInstanceClientImpl(searchQueryClient, objectMapper);

    // Given
    Long processDefinitionKey = 123L;
    String elementId = "task1";
    ElementInstance flownodeInstance1 =
        createFlownodeInstance("456", "123456", "187", "flowNodeName1", "tenantId1");
    ElementInstance flownodeInstance2 =
        createFlownodeInstance("789", "234567", "203", "flowNodeName2", "tenantId2");

    SearchResponse<ElementInstance> flownodeInstanceSearchResult =
        createSearchResult(flownodeInstance1, flownodeInstance2);
    SearchResponse<ElementInstance> flownodeInstanceEmptySearchResult = createEmptySearchResult();

    when(searchQueryClient.queryActiveFlowNodes(anyLong(), any(), any()))
        .thenReturn(flownodeInstanceSearchResult)
        .thenReturn(flownodeInstanceEmptySearchResult);

    // When
    List<ElementInstance> result =
        processInstanceClient.fetchActiveProcessInstanceKeyByDefinitionKeyAndElementId(
            processDefinitionKey, elementId);

    // Then
    assertThat(result.size()).isEqualTo(2);
    ElementInstance actualFlowNodeInstance1 = result.getFirst();
    assertThat(actualFlowNodeInstance1.getElementId()).isEqualTo(flownodeInstance1.getElementId());
    assertThat(actualFlowNodeInstance1.getProcessDefinitionKey())
        .isEqualTo(flownodeInstance1.getProcessDefinitionKey());
    assertThat(actualFlowNodeInstance1.getElementInstanceKey())
        .isEqualTo(flownodeInstance1.getElementInstanceKey());
    assertThat(actualFlowNodeInstance1.getProcessInstanceKey())
        .isEqualTo(flownodeInstance1.getProcessInstanceKey());
    assertThat(actualFlowNodeInstance1.getTenantId()).isEqualTo(flownodeInstance1.getTenantId());
  }

  private ElementInstance createFlownodeInstance(
      final String key,
      final String processInstanceKey,
      final String definitionKey,
      final String flowNodeId,
      final String tenantId) {
    final var item = new ElementInstanceResult();
    item.setElementInstanceKey(key);
    item.setProcessInstanceKey(processInstanceKey);
    item.setProcessDefinitionKey(definitionKey);
    item.setElementId(flowNodeId);
    item.setTenantId(tenantId);
    return new ElementInstanceImpl(item);
  }

  @Test
  public void testFetchVariablesByProcessInstanceKey() {
    ProcessInstanceClient processInstanceClient =
        new ProcessInstanceClientImpl(searchQueryClient, objectMapper);

    // Given
    Long processInstanceKey = 456L;
    Long elementInstanceKey = 789L;

    Variable variable1 = createVariable("12345", "var1", "value1");
    Variable variable2 = createVariable("67890", "var2", "value2");

    SearchResponse<Variable> variableSearchResult = createSearchResult(variable1, variable2);
    SearchResponse<Variable> variableEmptySearchResult = createEmptySearchResult();

    when(searchQueryClient.queryVariables(eq(processInstanceKey), eq(elementInstanceKey), any()))
        .thenReturn(variableSearchResult)
        .thenReturn(variableEmptySearchResult);

    // When
    Map<String, Object> result =
        processInstanceClient.fetchVariablesByProcessInstanceKey(
            processInstanceKey, elementInstanceKey);

    // Then
    assertThat(result.size()).isEqualTo(2);

    assertThat(result.get("var1")).isEqualTo("value1");
    assertThat(result.get("var2")).isEqualTo("value2");
  }

  private Variable createVariable(String key, String name, String value) {
    final var item = new VariableResult();
    item.setVariableKey(key);
    item.setScopeKey(key);
    item.setName(name);
    item.setValue(value);
    return new VariableImpl(item);
  }

  @SafeVarargs
  private <T> SearchResponse<T> createSearchResult(T... items) {
    final var page = new SearchResponsePageImpl((long) items.length, null, null);
    return new SearchResponseImpl<>(Arrays.asList(items), page);
  }

  private <T> SearchResponse<T> createEmptySearchResult() {
    final var page = new SearchResponsePageImpl(0L, null, null);
    return new SearchResponseImpl<>(Collections.emptyList(), page);
  }
}
