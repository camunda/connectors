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

import io.camunda.zeebe.client.api.search.response.FlowNodeInstance;
import io.camunda.zeebe.client.api.search.response.ProcessDefinition;
import io.camunda.zeebe.client.api.search.response.SearchQueryResponse;
import io.camunda.zeebe.client.api.search.response.Variable;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;

/** Wrapper over Zeebe client for Operate methods. Enables easier mocking and testing. */
public interface OperateClient {

  SearchQueryResponse<ProcessDefinition> queryProcessDefinitions(List<Object> paginationIndex);

  SearchQueryResponse<FlowNodeInstance> queryActiveFlowNodes(
      long processDefinitionKey, String elementId, List<Object> paginationIndex);

  SearchQueryResponse<Variable> queryVariables(
      long processInstanceKey, List<Object> variablePaginationIndex);

  BpmnModelInstance getProcessModel(long processDefinitionKey);
}
