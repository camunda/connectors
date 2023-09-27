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
package io.camunda.connector.api.inbound;

import java.util.List;

/**
 * An advanced interface for inbound intermediate connectors extending the {@link
 * InboundConnectorContext}. This interface provides added functionalities for detailed interactions
 * with active process instances.
 *
 * <p>In addition to the inherited capabilities, this interface permits:
 *
 * <ul>
 *   <li>Fetching context information of active process instances, specifically, the keys and
 *       corresponding variables of active processes.
 *   <li>Managing dynamic properties in real-time.
 *   <li>Transforming and validating properties specific to intermediate connectors using provided
 *       data.
 * </ul>
 *
 * <p>Intended for scenarios where adaptability to changing process conditions is needed or deeper
 * interactions with active processes are required.
 */
public interface InboundIntermediateConnectorContext extends InboundConnectorContext {

  /**
   * Gathers the context information for active process instances. This is achieved by obtaining the
   * process instance keys and their respective variables. It provides a comprehensive view into the
   * active processes, enabling informed decisions.
   *
   * @return A list of context information corresponding to each active process instance.
   */
  List<ProcessInstanceContext> getProcessInstanceContexts();
}
