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

import io.camunda.client.api.search.response.ElementInstance;
import java.util.List;

/**
 * Provides a proxy interface for retrieving information related to active process instances.
 *
 * <p>This interface defines methods to fetch information about active process instances. These
 * methods facilitate communication with Camunda's APIs and help in obtaining relevant data for
 * inbound connectors.
 */
public interface ProcessInstanceClient {

  /**
   * Fetches a list of active flow node instances associated with a given process definition key and
   * element ID.
   *
   * @param processDefinitionKey The unique identifier for the process definition.
   * @param elementId The identifier of the specific flow node element within the process
   *     definition.
   * @return A list of {@link ElementInstance} objects representing active process instances
   *     associated with the given process definition key and element ID. Returns an empty list if
   *     none are found.
   */
  List<ElementInstance> fetchActiveProcessInstanceKeyByDefinitionKeyAndElementId(
      final Long processDefinitionKey, final String elementId);
}
