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
package io.camunda.connector.runtime.core.inbound;

import io.camunda.zeebe.client.api.search.response.FlowNodeInstance;
import java.util.List;
import java.util.Map;

/**
 * Provides a proxy interface to interact with Camunda Operate's APIs for retrieving information
 * related to active process instances and their variables.
 *
 * <p>This interface defines methods to fetch information about active process instances and their
 * associated variables. These methods facilitate communication with Camunda Operate's APIs and help
 * in obtaining relevant data for inbound connectors.
 */
public interface ProcessInstanceClient {

  /**
   * Fetches a list of active flow node instances associated with a given process definition key and
   * element ID.
   *
   * @param processDefinitionKey The unique identifier for the process definition.
   * @param elementId The identifier of the specific flow node element within the process
   *     definition.
   * @return A list of {@link FlowNodeInstance} objects representing active process instances
   *     associated with the given process definition key and element ID. Returns an empty list if
   *     none are found.
   */
  List<FlowNodeInstance> fetchActiveProcessInstanceKeyByDefinitionKeyAndElementId(
      final Long processDefinitionKey, final String elementId);

  /**
   * Fetches the variables associated with a given active process instance identified by its key.
   *
   * @param processInstanceKey The unique identifier for the active process instance.
   * @return A {@link Map} containing the variables associated with the active process instance.
   */
  Map<String, Object> fetchVariablesByProcessInstanceKey(final Long processInstanceKey);
}
