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

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogWriter;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;

/**
 * Factory interface for creating {@link InboundConnectorContext} instances.
 *
 * <p>The factory produces context instances tailored for specific inbound connectors. The type of
 * context (standard or intermediate) is determined by the nature of the provided {@link
 * InboundConnectorExecutable}.
 */
public interface InboundConnectorContextFactory {

  /**
   * Creates an appropriate {@link InboundConnectorContext} instance based on the provided
   * parameters.
   *
   * @param connectorDetails the specific inbound connector data describing the connector and its
   *     properties
   * @param executableClass class representation of the executable connector in use
   * @param logRegistry log writer for runtime activity entries
   * @return a newly created {@link InboundConnectorContext}
   */
  <T extends InboundConnectorExecutable<?>> InboundConnectorContext createContext(
      final ValidInboundConnectorDetails connectorDetails,
      final Class<T> executableClass,
      final ActivityLogWriter logRegistry);
}
