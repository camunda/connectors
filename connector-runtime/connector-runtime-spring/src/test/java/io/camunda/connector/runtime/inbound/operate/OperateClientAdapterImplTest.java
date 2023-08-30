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
package io.camunda.connector.runtime.inbound.operate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.runtime.core.inbound.FlowNodeInstance;
import io.camunda.connector.runtime.core.inbound.OperateClientAdapter;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.dto.FlownodeInstance;
import io.camunda.operate.dto.SearchResult;
import io.camunda.operate.dto.Variable;
import io.camunda.operate.search.SearchQuery;
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
class OperateClientAdapterImplTest {
  @Mock private CamundaOperateClient camundaOperateClient;
  private ObjectMapper objectMapper;

  @BeforeEach
  public void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  public void testFetchFlowNodeInstanceByDefinitionKeyAndElementId() throws Exception {
    OperateClientAdapter operateClientAdapter =
        new OperateClientAdapterImpl(camundaOperateClient, objectMapper);

    // Given
    Long processDefinitionKey = 123L;
    String elementId = "task1";
    // io.camunda.operate.dto.FlownodeInstance
    FlownodeInstance flownodeInstance1 =
        createFlownodeInstance(456L, 123456L, 187L, "flowNodeId1", "flowNodeName1", "tenantId1");
    FlownodeInstance flownodeInstance2 =
        createFlownodeInstance(789L, 234567L, 203L, "flowNodeId2", "flowNodeName2", "tenantId2");

    SearchResult<FlownodeInstance> flownodeInstanceSearchResult =
        createSearchResult(flownodeInstance1, flownodeInstance2);
    SearchResult<FlownodeInstance> flownodeInstanceEmptySearchResult = createEmptySearchResult();
    flownodeInstanceSearchResult.setSortValues(List.of(456L, 789L));

    when(camundaOperateClient.search(any(SearchQuery.class), eq(FlownodeInstance.class)))
        .thenReturn(flownodeInstanceSearchResult)
        .thenReturn(flownodeInstanceEmptySearchResult);

    // When
    List<FlowNodeInstance> result =
        operateClientAdapter.fetchActiveProcessInstanceKeyByDefinitionKeyAndElementId(
            processDefinitionKey, elementId);

    // Then
    assertThat(result.size()).isEqualTo(2);
    FlowNodeInstance actualFlowNodeInstance1 = result.get(0);
    assertThat(actualFlowNodeInstance1.flowNodeId()).isEqualTo(flownodeInstance1.getFlowNodeId());
    assertThat(actualFlowNodeInstance1.flowNodeName())
        .isEqualTo(flownodeInstance1.getFlowNodeName());
    assertThat(actualFlowNodeInstance1.processDefinitionKey())
        .isEqualTo(flownodeInstance1.getProcessDefinitionKey());
    assertThat(actualFlowNodeInstance1.key()).isEqualTo(flownodeInstance1.getKey());
    assertThat(actualFlowNodeInstance1.processInstanceKey())
        .isEqualTo(flownodeInstance1.getProcessInstanceKey());
    assertThat(actualFlowNodeInstance1.tenantId()).isEqualTo(flownodeInstance1.getTenantId());
  }

  private FlownodeInstance createFlownodeInstance(
      Long key,
      Long processInstanceKey,
      final long definitionKey,
      final String flowNodeId,
      final String flowNodeName,
      final String tenantId) {
    FlownodeInstance instance = new FlownodeInstance();
    instance.setKey(key);
    instance.setProcessInstanceKey(processInstanceKey);
    instance.setProcessDefinitionKey(definitionKey);
    instance.setFlowNodeId(flowNodeId);
    instance.setFlowNodeId(flowNodeName);
    instance.setFlowNodeId(tenantId);
    return instance;
  }

  @Test
  public void testFetchVariablesByProcessInstanceKey() throws Exception {
    OperateClientAdapter operateClientAdapter =
        new OperateClientAdapterImpl(camundaOperateClient, objectMapper);

    // Given
    Long processInstanceKey = 456L;

    Variable variable1 = createVariable(12345L, "var1", "value1");
    Variable variable2 = createVariable(67890L, "var2", "value2");

    SearchResult<Variable> variableSearchResult = createSearchResult(variable1, variable2);
    SearchResult<Variable> variableEmptySearchResult = createEmptySearchResult();

    when(camundaOperateClient.search(any(SearchQuery.class), eq(Variable.class)))
        .thenReturn(variableSearchResult)
        .thenReturn(variableEmptySearchResult);

    // When
    Map<String, Object> result =
        operateClientAdapter.fetchVariablesByProcessInstanceKey(processInstanceKey);

    // Then
    assertThat(result.size()).isEqualTo(2);

    assertThat(result.get("var1")).isEqualTo("value1");
    assertThat(result.get("var2")).isEqualTo("value2");
  }

  private Variable createVariable(Long key, String name, String value) {
    Variable variable = new Variable();
    variable.setKey(key);
    variable.setName(name);
    variable.setValue(value);
    return variable;
  }

  @SafeVarargs
  private <T> SearchResult<T> createSearchResult(T... items) {
    SearchResult<T> searchResult = new SearchResult<>();
    searchResult.setItems(Arrays.asList(items));
    searchResult.setTotal(items.length);
    return searchResult;
  }

  private <T> SearchResult<T> createEmptySearchResult() {
    SearchResult<T> searchResult = new SearchResult<>();
    searchResult.setItems(Collections.emptyList());
    searchResult.setTotal(0);
    return searchResult;
  }
}
