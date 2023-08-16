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

import io.camunda.connector.api.inbound.operate.ProcessInstance;
import java.util.List;

/**
 * Extended context object for inbound connectors, providing additional capabilities specific to
 * intermediate processing stages. This context provides methods to interact with process instances
 * associated with it, including the ability to fetch the latest state of these instances
 * dynamically. The method {@link #getProcessInstances()} returns a list of process instances with
 * their associated variables. Note that the variables associated with each process instance are
 * updated every time the method is invoked, reflecting the latest state of the process instances at
 * the time of the call.
 */
public interface InboundIntermediateConnectorContext extends InboundConnectorContext {
  /**
   * Retrieves a list of process instances associated with this context, including their latest
   * variables. The variables are dynamically updated every time this method is invoked, allowing
   * the connector to have access to the most recent state of the process instances.
   *
   * @return a list of {@link ProcessInstance} with updated variables
   */
  List<ProcessInstance> getProcessInstances();
}
