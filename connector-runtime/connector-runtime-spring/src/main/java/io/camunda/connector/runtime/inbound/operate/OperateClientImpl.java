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

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.search.response.FlowNodeInstance;
import io.camunda.zeebe.client.api.search.response.ProcessDefinition;
import io.camunda.zeebe.client.api.search.response.SearchQueryResponse;
import io.camunda.zeebe.client.api.search.response.Variable;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.ByteArrayInputStream;
import java.util.List;

public class OperateClientImpl implements OperateClient {

  private static final int PAGE_SIZE = 50;

  private final ZeebeClient zeebeClient;

  public OperateClientImpl(ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }

  @Override
  public SearchQueryResponse<ProcessDefinition> queryProcessDefinitions(
      List<Object> paginationIndex) {
    final var query =
        zeebeClient.newProcessDefinitionQuery().sort(s -> s.processDefinitionKey().desc());
    if (paginationIndex != null) {
      query.page(p -> p.limit(PAGE_SIZE).searchAfter(paginationIndex));
    } else {
      query.page(p -> p.limit(PAGE_SIZE));
    }
    return query.send().join();
  }

  @Override
  public SearchQueryResponse<FlowNodeInstance> queryActiveFlowNodes(
      long processDefinitionKey, String elementId, List<Object> paginationIndex) {
    final var query =
        zeebeClient
            .newFlownodeInstanceQuery()
            .filter(
                i ->
                    i.processDefinitionKey(processDefinitionKey)
                        .flowNodeId(elementId)
                        .state("ACTIVE"));
    if (paginationIndex != null) {
      query.page(p -> p.limit(PAGE_SIZE).searchAfter(paginationIndex));
    } else {
      query.page(p -> p.limit(PAGE_SIZE));
    }
    return query.send().join();
  }

  @Override
  public SearchQueryResponse<Variable> queryVariables(
      long processInstanceKey, List<Object> variablePaginationIndex) {
    final var query =
        zeebeClient
            .newVariableQuery()
            .filter(v -> v.processInstanceKey(processInstanceKey).scopeKey(processInstanceKey));
    if (variablePaginationIndex != null) {
      query.page(p -> p.limit(PAGE_SIZE).searchAfter(variablePaginationIndex));
    } else {
      query.page(p -> p.limit(PAGE_SIZE));
    }
    return query.send().join();
  }

  @Override
  public BpmnModelInstance getProcessModel(long processDefinitionKey) {
    final String xml =
        zeebeClient.newProcessDefinitionGetXmlRequest(processDefinitionKey).send().join();
    return Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes()));
  }
}
